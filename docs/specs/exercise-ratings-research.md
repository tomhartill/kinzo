# Spec: exercise effectiveness ratings (research)

Status: **planned**, October 2026. This is the plan for researching how well each exercise trains each
muscle. It doubles as the outline of a public methodology page, because "every rating shows its
evidence" is something few training apps can claim. The app feature that uses the ratings is
[workout-creator.md](workout-creator.md).

Sources marked *(verify)* were found while drafting and need reading in full before a rating
relies on them.

## Goal

For every exercise in the catalogue, a 1 to 5 rating for each muscle it trains, an evidence grade,
the studies behind it, and one plain sentence explaining it. The ratings feed the workout
creator's volume maths, the exercise detail screen and the website.

## The rating scale

The rating answers: *if you do a hard set of this exercise, how much does it count as training
for this muscle?*

| Rating | Meaning | Counts as | Example |
| --- | --- | --- | --- |
| 5 | Prime mover, loaded through a long range, growth shown in training studies | 1 set | Mid chest on flat bench |
| 4 | Major contributor | 0.75 set | Upper chest on flat bench |
| 3 | Meaningful synergist | 0.5 set | Triceps on bench press |
| 2 | Minor contributor | 0.25 set | Biceps on rows |
| 1 | Stabilises only | 0 | Abs on overhead press |

The "counts as" column lines up with how recent meta-regressions count a set for a muscle that is
not the main target as half a set ([Pelland et al. 2024](https://sportrxiv.org/index.php/server/preprint/download/537/1148/1071)).

## Evidence grades

Shown next to each rating, so users and the website never over-claim.

| Grade | Basis |
| --- | --- |
| A | Training studies that measured growth (ultrasound, MRI) for this exercise or a close variant |
| B | Biomechanics: muscle length, moment arms, joint actions and range of motion |
| C | Acute EMG or muscle-swelling studies only |
| D | Expert consensus or inference from a similar exercise |

Why EMG ranks low: how loudly a muscle "fires" in one session is not a validated predictor of how
much it grows over weeks ([Vigotsky et al. 2022, Sports Medicine](https://cris.iucc.ac.il/en/publications/longing-for-a-longitudinal-proxy-acutely-measured-surface-emg-amp/)).
A lot of fitness content ranks exercises by EMG; saying clearly that Kinzo doesn't is part of the story.

## Worked example: bench press

Tom's starting assumption was 5 out of 5 for upper chest, chest, front delts and triceps. The
evidence points to a spread instead (Tom agreed, October 2026):

| Muscle | Rating | Grade | Reasoning |
| --- | --- | --- | --- |
| Mid and lower chest (sternal pec) | 5 | A | Flat pressing loads it through a long range; trained flat bench grows the chest. |
| Upper chest (clavicular pec) | 4 | A | It works on flat bench, but in a training study only the incline group grew it noticeably more ([Chaves et al. 2020, Int J Exerc Sci](https://www.strongerbyscience.com/?p=57858) *(verify)*). Incline bench gets the 5. The two heads have separate nerve supplies, so treating them as separate muscles is justified. |
| Front delts | 3 to 4 | B | A strong shoulder flexor in pressing, but range is short and it gets heavy volume from overhead work. Decide once the delt studies are read. |
| Triceps | 3 | A | They grow from bench press, but much less than from dedicated triceps work, and the long head (which crosses the shoulder) is barely lengthened. Overhead extensions grew the triceps clearly more than pushdowns ([Maeo et al. 2023](https://pubmed.ncbi.nlm.nih.gov/35819335/)), which is why the long head is its own muscle in the model. |

Discussion of the flat vs incline evidence, including the limits of the acute studies
([Albarello et al. 2022](https://www.strongerbyscience.com/?p=57858) *(verify)*, Trebs et al. 2010,
Rodríguez-Ridao et al. 2020), is summarised by Stronger By Science.

## Method

1. **Muscle list first.** Agree the ~30 muscles and 12 groups (see the data model in workout-creator.md).
2. **Exercise groups, not ~915 exercises.** Most catalogue entries are variations, and the
   catalogue already folds them into groups (`exercise_group`, such as every bench press or chest
   press). Research and rate each group; each exercise inherits its group's ratings unless it has
   its own row, with small, noted adjustments (grip, stance, angle, range). An incline press that
   trains the upper chest differently gets its own override rows.
3. **Per exercise group, collect:** training studies with growth measures (grade A), then
   biomechanics (B), then EMG (C). Record each source once in a shared bibliography with its
   design and sample.
4. **Draft, then review.** Claude drafts each group's ratings with sources and a one-line note
   into `tools/catalogue/muscle_ratings.csv`. Tom (or a coach he trusts) reviews them group by
   group. Disagreements are written down, not averaged away.
5. **Prioritise by use.** The top ~150 exercises (the ones in Kinzo's ready-made workouts plus the
   most common gym lifts) first; the long tail can ship with grade D inherited ratings and be
   upgraded over time.
6. **Keep it current.** Each rating keeps its sources, so new studies can be added and the rating
   revisited. A changelog on the website ("ratings updated October 2026") shows it is maintained.

## Stability score

A second, simpler score per **exercise**: 1 to 5, where 5 is fully guided and supported
(Smith machine, most machines) and 1 is unstable or single-limb and unsupported. The scale and
its use in the creator are in workout-creator.md. Unlike muscle ratings it differs between
exercises in one group (dumbbell bench 3, Smith bench 5), so every exercise is scored.

Research questions:

- Studies comparing stable and unstable versions of the same lift (machine vs free weight, Smith
  vs barbell, dumbbell vs barbell) on load lifted, muscle growth and strength transfer.
- Whether more stable versions grow the target muscle as well or better, and whether free weight
  versions transfer better to free weight strength.
- Most scores can be assigned from how the exercise is set up (guided path, bench support, one or
  two limbs), so grade B or D is fine for most; the studies matter for the website claims.

## Starting bibliography

To read in full and extend as exercise groups are researched.

- Vigotsky et al. 2022. Acutely measured surface EMG amplitude is not a validated predictor of
  muscle hypertrophy. *Sports Medicine.* Basis for the grading.
- Pelland et al. 2024. Resistance training dose-response meta-regressions on weekly volume and
  frequency. *SportRxiv preprint.* Basis for fractional set counting. *(verify final publication)*
- Maeo et al. 2023. Triceps hypertrophy greater with overhead than neutral arm position.
  *European Journal of Sport Science.* [PubMed](https://pubmed.ncbi.nlm.nih.gov/35819335/)
- Maeo et al. 2021. Hamstrings hypertrophy greater with seated than lying leg curls.
  *Medicine & Science in Sports & Exercise.* *(verify)*
- Chaves et al. 2020. Flat vs incline bench and regional chest growth. *Int J Exerc Sci.* *(verify)*
- Roberts et al. 2020. Sex differences in resistance training: systematic review and
  meta-analysis. [PubMed](https://pubmed.ncbi.nlm.nih.gov/32218059/). Used by the creator, not the ratings.
- Schoenfeld et al. 2017. Dose-response of weekly sets and hypertrophy. *J Sports Sci.* *(verify)*

Where the data can't come from: commercial databases such as ExRx may be read for orientation but
not copied; their content is copyrighted. free-exercise-db (the current catalogue source) is public
domain but only has primary/secondary lists, which become a cross-check.

## Website and marketing

- A public **Kinzo Exercise Index** page: the scale, the grades, and per exercise the ratings with
  their sources. Searchable, and linkable from the app's exercise detail screen.
- Honest claims only: "rated from published research, with the evidence shown". Avoid "scientifically
  proven best exercises".
- Good story angles: "Why we don't rank exercises by EMG", "Bench press doesn't max out your
  triceps", "Same programme, men and women: what the research says".

## On hold: general fatigue score

Tom wants this later. Open questions to research when it's picked up:

- What "fatigue" should mean: systemic (whole body, recovery between sessions), local (muscle),
  or joint and spine loading. Probably separate scores.
- Evidence on recovery time after heavy compound lifts versus isolation work, and after
  lengthened versus shortened ranges.
- A stimulus-to-fatigue view: rating ÷ fatigue as a way to rank swaps in the creator.
- Whether reliable data exists at all, or whether this stays grade D for a long time.
