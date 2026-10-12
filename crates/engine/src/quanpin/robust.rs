//! 无完整切分的长输入按字母位置解词图，编辑成本与词频共同保留路径。

use std::collections::HashSet;

use crate::dictionary::pinyin::PinyinDatabase;
use crate::lattice::decode::{decode_letter_edges, LatticeOptions, SentencePath, TypoEdge};
use crate::pinyin::syllables::{intact_pinyin_list, normalize_umlaut_aliases};
use crate::pinyin::typos::keys_adjacent;
use crate::types::autocorrect_type;

const MAX_LETTERS: usize = 48;
const SYLLABLE_EDGES: usize = 16;
const PHRASE_SYLLABLES: usize = 3;
const PHRASE_BEAM: usize = 24;
const QUERY_KEYS: usize = 512;
const ROWS: usize = 8;

#[derive(Clone)]
struct Reading {
    end: usize,
    key: String,
    cost: u16,
    rank: f64,
}

/// 单音节加权 Damerau 距离，四分之一编辑为单位；非邻键替换更贵。
fn distance(raw: &[u8], target: &[u8], types: u32) -> u16 {
    const INF: u16 = 100;
    let mut table = [[INF; 8]; 9];
    table[0][0] = 0;
    for i in 0..=raw.len() {
        for j in 0..=target.len() {
            let here = table[i][j];
            if i < raw.len() && types & autocorrect_type::INSERTION != 0 {
                table[i + 1][j] = table[i + 1][j].min(here + 4);
            }
            if j < target.len() && types & autocorrect_type::DELETION != 0 {
                table[i][j + 1] = table[i][j + 1].min(here + 4);
            }
            if i < raw.len() && j < target.len() {
                let cost = if raw[i] == target[j] {
                    0
                } else if types & autocorrect_type::NEIGHBOR == 0 {
                    INF
                } else if keys_adjacent(raw[i], target[j]) {
                    4
                } else {
                    7
                };
                table[i + 1][j + 1] = table[i + 1][j + 1].min(here + cost);
            }
            if i + 1 < raw.len()
                && j + 1 < target.len()
                && raw[i] == target[j + 1]
                && raw[i + 1] == target[j]
                && types & autocorrect_type::TRANSPOSITION != 0
            {
                table[i + 2][j + 2] = table[i + 2][j + 2].min(here + 3);
            }
        }
    }
    table[raw.len()][target.len()]
}

/// 固定词库键预算；不接受分隔符、不改变普通短输入，路径消费整个原串。
pub(super) fn decode(
    database: &PinyinDatabase,
    raw: &str,
    types: u32,
    options: &LatticeOptions<'_>,
) -> Vec<SentencePath> {
    if !(8..=MAX_LETTERS).contains(&raw.len())
        || types == 0
        || !raw.bytes().all(|ch| ch.is_ascii_lowercase())
    {
        return Vec::new();
    }
    let keys: Vec<String> = intact_pinyin_list()
        .iter()
        .map(|part| {
            let mut parts = vec![(*part).to_owned()];
            normalize_umlaut_aliases(&mut parts);
            parts.remove(0)
        })
        .collect::<HashSet<_>>()
        .into_iter()
        .collect();
    let mut rows = database.query_exact_keys_per_key(&keys, ROWS);
    // 出货词库的单字频数超过旧固定切分的归一量；概率必须小于一，避免拆成更多字反而加分。
    let unigram_z = rows
        .values()
        .flatten()
        .map(|row| row.weight.max(1) as f64)
        .sum::<f64>()
        .max(options.unigram_z);
    let mut syllables: Vec<&str> = rows.keys().map(String::as_str).collect();
    syllables.sort_unstable();
    let mut readings = Vec::with_capacity(raw.len());
    for start in 0..raw.len() {
        let mut choices = Vec::new();
        for end in start + 1..=raw.len().min(start + 7) {
            let piece = &raw.as_bytes()[start..end];
            for &key in &syllables {
                let mut cost = distance(piece, key.as_bytes(), types);
                // 合法尾部补全不计作两次漏字，但仍付少量猜测成本。
                if end == raw.len() && key.starts_with(&raw[start..end]) {
                    cost = cost.min(2);
                }
                if cost > 8 || (piece.len() == 1 && cost != 0 && end != raw.len()) {
                    continue;
                }
                let weight = rows[key].first().map_or(1, |row| row.weight.max(1));
                choices.push(Reading {
                    end,
                    key: key.to_owned(),
                    cost,
                    rank: 2.0 * f64::from(cost) - 0.35 * (weight as f64).ln(),
                });
            }
        }
        choices.sort_by(|a, b| {
            a.rank
                .total_cmp(&b.rank)
                .then_with(|| a.key.cmp(&b.key))
                .then(a.end.cmp(&b.end))
        });
        // 所有字面边保留，编辑边有界；相同读音的不同消费长度不能合并。
        let mut selected = HashSet::new();
        let mut ends = HashSet::new();
        for (at, choice) in choices.iter().enumerate() {
            if choice.cost != 0 && ends.insert(choice.end) {
                selected.insert(at);
            }
        }
        for (at, choice) in choices.iter().enumerate() {
            if choice.cost != 0 && selected.len() < SYLLABLE_EDGES {
                selected.insert(at);
            }
        }
        let mut at = 0;
        choices.retain(|choice| {
            let keep = choice.cost == 0 || selected.contains(&at);
            at += 1;
            keep
        });
        readings.push(choices);
    }
    drop(syllables);

    let mut spans = Vec::new();
    let mut requested = HashSet::new();
    let mut query_keys = Vec::new();
    let keys_per_start = QUERY_KEYS / raw.len();
    for start in 0..raw.len() {
        let mut start_keys = 0;
        let mut frontier = readings[start].clone();
        for depth in 1..=PHRASE_SYLLABLES {
            frontier.sort_by(|a, b| {
                a.rank
                    .total_cmp(&b.rank)
                    .then_with(|| a.key.cmp(&b.key))
                    .then(a.end.cmp(&b.end))
            });
            frontier.truncate(PHRASE_BEAM);
            let mut next = Vec::new();
            for span in frontier {
                if depth > 1 && start_keys < keys_per_start && requested.insert(span.key.clone()) {
                    query_keys.push(span.key.clone());
                    start_keys += 1;
                }
                if depth < PHRASE_SYLLABLES && span.end < raw.len() {
                    for tail in &readings[span.end] {
                        let cost = span.cost + tail.cost;
                        if cost <= 12 {
                            next.push(Reading {
                                end: tail.end,
                                key: format!("{}'{}", span.key, tail.key),
                                cost,
                                rank: span.rank + tail.rank,
                            });
                        }
                    }
                }
                spans.push((start, span));
            }
            frontier = next;
        }
    }
    rows.extend(database.query_exact_keys_per_key(&query_keys, ROWS));
    let mut edges = Vec::new();
    for (start, span) in spans {
        for row in rows.get(&span.key).into_iter().flatten() {
            edges.push(TypoEdge {
                start,
                end: span.end,
                key: row.key.clone(),
                value: row.value.clone(),
                weight: row.weight,
                penalty: f64::from(span.cost) * 2.0,
            });
        }
    }
    let mut options = options.clone();
    options.beam = 24;
    options.nbest = 24;
    options.unigram_z = unigram_z;
    decode_letter_edges(raw.len(), &edges, &options)
        .into_iter()
        .filter(|path| path.words.len() >= 2)
        .take(6)
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn distance_respects_switches_and_multiple_edits() {
        let all = 15;
        assert_eq!(distance(b"sahng", b"shang", all), 3);
        assert_eq!(distance(b"shanng", b"shang", all), 4);
        assert_eq!(distance(b"wi", b"wo", all), 4);
        assert_eq!(distance(b"xb", b"xi", all), 7);
        assert!(distance(b"wi", b"wo", 0) > 8);
    }
}
