# Specs

| Version | Status | Last updated |
|---|---|---|
| 1.0.0 | Accepted | 2026-11-25 |

This document sets the rules every spec in this repository follows and gives the template for feature specs. It is itself a spec, so it follows the rules on versioning and the changelog below.

## What a spec is

A spec is the single source of truth for what it describes. Code, tickets and conversations can drift; the spec is what they are measured against.

- **It stands alone.** A spec never links to tickets, design files, other clients or chat threads. Anything a reader needs is written in the spec, including the parts of the server contract the feature uses.
- **It describes the thing, not its delivery.** A spec has no opinion on how the work is split up or scheduled. It contains no phases, milestones, PR plans or "v1 / later" splits. Something the feature doesn't do is listed as out of scope, with no promise of when it might be done.
- **Code and spec agree.** When they disagree, the same PR either fixes the code or changes the spec, and bumps the spec's version.

## Where specs live

**Feature specs** live at `docs/features/<feature>.md`, where `<feature>` is a short kebab-case name such as `search`. Their images live at `docs/features/images/<feature>/<kebab-name>.png` and are embedded in the spec.

Other specs, such as this one, live wherever their subject belongs and don't follow the feature-spec path or template.

## Wording

Requirements use the keywords below, always in capitals, with these meanings (from RFC 2119):

| Keyword | Meaning |
|---|---|
| **MUST** / **MUST NOT** | An absolute requirement. Code that breaks it is wrong. |
| **SHOULD** / **SHOULD NOT** | The default. Departing from it needs a reason, written in the PR that departs. |
| **MAY** | Truly optional. Either choice is correct. |

Lowercase "must", "should" and "may" carry no special meaning, so avoid them in requirements.

Each requirement is a single bullet that someone who hasn't read the code can check by using the app. When the reason for a requirement isn't obvious and matters to the reader, give it in one line beside the requirement.

## Versioning

Every spec carries a [Semantic Versioning](https://semver.org/) version in its header table, next to its status and the date it last changed. Every change to a spec bumps the version:

| Bump | When | Examples |
|---|---|---|
| **MAJOR** | A requirement is removed or contradicted, so behaviour built to the previous version is now wrong. | Dropping the cover from a row; changing what a tap opens. |
| **MINOR** | Requirements or capability are added without making any existing one wrong. | A new result type; a new error state. |
| **PATCH** | Nothing about the behaviour changes. | Wording, typos, clarifications; a refreshed render that shows the same design. |

A spec still being written is a draft, versioned `0.y.z`. The first version agreed to be ready to build is `1.0.0`.

**Status** is one of:

- **Draft:** still being written; not ready to build against.
- **Accepted:** agreed; code is measured against it.
- **Deprecated:** the feature is gone or replaced; kept for history.

## Changelog

Every spec ends with a changelog, newest version first. Each entry gives the version, the date, what changed, and why.

`1.0.0` reads "Initial spec." The reasoning behind the initial design is not logged here; where a reason matters, it sits beside its requirement. The changelog is for deliberate changes made after that, for example:

> **2.0.0** (2027-03-02)
> - Book rows no longer show a cover. Why: the cover made each row too tall, so too few results fit on screen.

## Images

- Renders show what a screen looks like. Don't restate in words what a render shows. Use call-outs only for what a render can't show, such as behaviour, limits and edge cases.
- A change to how something looks updates its render in the same change.
- Name images by what they show (`results-phone-dark.png`), not by order or date.

## Tickets and specs

Tickets and specs are separate. A ticket is often written before a spec exists, so neither points to the other.

- A ticket is a high-level description of a feature, with a few call-outs and high-level requirements. Whoever writes it may add as much detail as they think useful.
- When a spec is written, the details in its tickets are input to it. From then on, the spec is the source of truth.

## Feature spec template

Every section is required. When a section doesn't apply, write "None." under its heading rather than leaving it out, so an absent section is clearly a choice and not something forgotten.

```markdown
# <Feature name>

| Version | Status | Last updated |
|---|---|---|
| 1.0.0 | Accepted | YYYY-MM-DD |

## Summary

What the feature is and who it's for, in two or three sentences.

## Scope

**In scope**

- …

**Out of scope**

- …

## Glossary

| Term | Meaning |
|---|---|
| … | … |

---

# Part 1: Product

## Screens

One subsection per screen or surface: the embedded render(s), then call-outs for what the render can't show.

### <Screen name>

![<Screen name>](images/<feature>/<kebab-name>.png)

- …

## States and copy

Every state the feature can be in, with its exact copy. Requirements refer to these states by name rather than repeating the copy.

| State | When | Copy | Action |
|---|---|---|---|
| … | … | … | … |

## Requirements

MUST / SHOULD / MAY bullets, grouped by topic. Each one is checkable by using the app.

### <Topic>

- The <thing> MUST …
- The <thing> SHOULD …

---

# Part 2: Engineering

## Server contract

Each endpoint the feature calls: method and path, parameters, the response fields the feature reads, and how each error status is handled. Fields the feature ignores are left out.

## Architecture

The layers, models and routes the feature adds or changes, and the existing code it reuses.

## Constants

| Name | Value | Meaning |
|---|---|---|
| … | … | … |

---

## Changelog

**1.0.0** (YYYY-MM-DD)

- Initial spec.
```

---

## Changelog

**1.0.0** (2026-11-25)

- Initial spec.
