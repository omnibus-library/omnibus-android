# Pull Requests

## What a PR Is
* A pull request delivers one meaningful change to what the user experiences: a feature slice they can use, or a fix they can notice.
* Split a feature by user impact, never by the architecture layers it touches. A PR that only adds a data source, a ViewModel or an unwired screen is not a slice.
* Each PR builds everything its own behavior needs, end to end (data, state, UI and tests), and nothing that only a later PR uses. Don't add a repository method "for later".
* Each PR leaves `main` releasable. Releases are cut from `main`, so a merged PR can reach users on its own. It may be visibly incomplete, such as a toolbar with only some of its buttons, but never broken, dead or half-wired. Don't hide unfinished work behind a feature flag unless the ticket asks for one.
* There is no file or line limit. If a PR feels too big, check whether it holds more than one user-visible capability, and split it by capability.

### Example: Reader Highlight Menu

Don't split it by layer:
1. Add the data source
2. Add the state holders
3. Add the UI
4. Hook the UI into the state holders

Split it by what the user gets:
1. Tapping a highlight opens a minimal highlight toolbar that shows the highlight's current color
2. The toolbar gets a delete button, with everything behind it
3. Users can pick a different color on the toolbar to change the highlight's color
4. The toolbar gets a copy button, with everything behind it
5. The toolbar gets a switcher for moving between overlapping highlights

## Work With No User-Visible Change
* A refactor that a feature needs ships inside the PR that needs it, not as a lead-in PR of its own.
* A standalone `refactor`, `chore`, `ci`, `test` or `build` PR is fine when that work is the ticket itself.
* A bug fix is one PR, however many layers it touches.

## Tickets and PRs
* One ticket can produce several PRs. Every PR except the last says `Part of #N`, and only the last one says `Closes #N`, so the ticket closes when its final slice merges.
* Each PR's description names the user-visible change it delivers and, when there are several, its place among them ("slice 2 of 5: delete button").

## Stacking
* When one feature is delivered as several PRs, open them as one linear stack with [GitHub stacked PRs](https://github.com/github/gh-stack) (`gh stack`), each PR based on the one below it. Do this even when two slices don't depend on each other, such as copy and delete above.
* Order the stack by dependency first, then by what users should get first if the stack stalls partway, so that every merged prefix is the most useful release it can be.
* Merge a stack from the bottom up.
* Unrelated work (separate features, separate fixes) goes in separate PRs based on `main`.
