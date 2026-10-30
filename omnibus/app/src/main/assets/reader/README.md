# Reader assets

The EPUB reader's page, copied from the iOS client's
[`omnibus-ios/omnibus/Reader/Web/`](https://github.com/omnibus-library/omnibus/tree/497db1af94b4feca723145f3b4c450f5a7053784/omnibus-ios/omnibus/Reader/Web).
This is the iOS copy, not `frontend/assets/vendor/`: the iOS glue has diverged from the web one and the
reader depends on its additions (`seek(fraction)`, `chapterPagesLeft`, `applyHostGround`,
`clearSelection`, annotation-tap callbacks and note underlines).

**Source commit:** `497db1af94b4feca723145f3b4c450f5a7053784` (omnibus-library/omnibus, the latest commit touching `Reader/Web/` as of 2026-10-28)

| File | State |
|---|---|
| `epub.min.js`, `jszip.min.js`, `epub-reader-glue.js` | Byte-identical to the source commit. |
| `reader-fonts.css`, `*.woff2` | Byte-identical. The glue injects the sheet into every section through its `fontsHref` option. |
| `reader.html` | Adapted, see below. |

## What differs in `reader.html`

Everything else in the file is the iOS page, so a later sync can diff against the source commit and
re-apply just these two changes:

1. **Message bridge.** `window.webkit.messageHandlers.omnibus.postMessage({type, payload})` became
   `window.omnibus.postMessage(JSON.stringify({type, payload}))`, the object that
   `WebViewCompat.addWebMessageListener` installs. A web message carries a single string.
2. **Insets.** `env(safe-area-inset-*)` became `var(--inset-*, 0px)`. `env()` is unreliable in an
   Android WebView, so the host pushes the system-bar insets in as `--inset-top`, `--inset-bottom`,
   `--inset-left` and `--inset-right` (CSS px). The 58px / 10px reserved bands are unchanged.

## Syncing from iOS

Copy the iOS files over the byte-identical ones, merge any change to `reader.html` by hand, and
update the source commit above.
