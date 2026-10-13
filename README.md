# Pocket Markdown

A small Android app for reading Markdown files. Opens `.md` files from the
file picker, from **Open with** in My Files, or via **Share**.

## Get the APK

1. Push this folder to a GitHub repository.
2. GitHub Actions builds it automatically (tab **Actions**, about 3 minutes).
3. On your phone, open the repository's **Releases** page and tap `PocketMarkdown.apk`.
4. Allow your browser to install unknown apps when Android asks.

## Build locally instead

Open the folder in Android Studio. Before building, download
`marked.min.js` (marked 12.0.2) and `purify.min.js` (DOMPurify 3.1.6) into
`app/src/main/assets/`. The URLs are in `.github/workflows/build-apk.yml`.
