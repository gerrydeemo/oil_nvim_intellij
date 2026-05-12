# Oil for IntelliJ - Developer & User Guide

Oil for IntelliJ brings the `oil.nvim` workflow to the IntelliJ Platform. It allows you to edit your project's directory structure as if it were a plain text buffer, complete with native IDE icons and syntax highlighting.

---

## 🚀 User Guide

### 1. Installation & Setup
To set up this project on a new machine:

1.  **Install Java 17:** Ensure you have JDK 17 installed (e.g., Amazon Corretto or Azul Zulu).
2.  **Clone the Repository:** 
    ```bash
    git clone https://github.com/gerrydempsey/OilIntelliJ.git
    cd OilIntelliJ
    ```
3.  **Open in IntelliJ IDEA:**
    - Open IntelliJ IDEA.
    - Select **Open** and choose the `OilIntelliJ` folder.
    - Wait for the "Gradle Sync" to complete (watch the progress bar in the bottom right).
4.  **Run the Plugin:**
    - Open the **Gradle** tool window (usually on the right side).
    - Navigate to `Tasks` > `intellij` > `runIde`.
    - Double-click `runIde` to launch a development instance of the IDE with the plugin active.

### ⚠️ Critical Stability Fix (macOS / Apple Silicon)
IntelliJ's current Metal rendering engine can crash (SIGSEGV) when UI components like editor tabs refresh rapidly during filesystem syncs. **You must apply this fix for a stable experience:**

1.  Open your main IntelliJ IDEA instance.
2.  Go to **Help -> Edit Custom VM Options...**
3.  Add the following lines (ensure there are **no leading spaces**):
    ```text
    -Dsun.java2d.metal=false
    -Dawt.mac.flushBuffers.invokeLater=true
    ```
4.  **Restart the IDE.**

### 2. Usage
- **Open Oil:** Press `Ctrl + Alt + O` (or find "Open Oil" in the File menu). This opens the current directory in a temporary "Oil Buffer" (an `.oil` file).
- **Navigation:**
    - Use the `Open Selected` action to drill into folders or open files.
    - Use `Open Parent` to move up the directory tree.
- **Editing:**
    - **Rename:** Change the text of a filename on its line.
    - **Create:** Add a new line with a filename. End it with `/` to create a directory.
    - **Delete:** Delete a line to delete the corresponding file on disk.
- **Save:** Press `Cmd + S` (Save All). The plugin will detect changes in the `.oil` buffer and synchronize them to your filesystem.

### 3. Keybindings (IdeaVim)
If you use the **IdeaVim** plugin, you can add the following mappings to your `.ideavimrc` (or refer to the `config.txt` file in this repo) to match the `oil.nvim` workflow:

```vim
" Open Oil (current directory)
nmap - <Action>(com.github.gerrydempsey.oil.OpenOilAction)

" Open selected file/folder
nmap <Enter> <Action>(com.github.gerrydempsey.oil.OpenSelectedAction)

" Open parent directory
nmap _ <Action>(com.github.gerrydempsey.oil.OpenParentAction)

" Flash/Search integration (requires Flash plugin)
nmap s <Action>(flash.search)
xmap s <Action>(flash.search)
```

---

## 🛡 Advanced Operations & Safety

### 1. Moving Files (Cut/Paste)
- **Same Buffer:** Renaming a line is effectively a move within the same directory.
- **Cross-Buffer:** If you cut a line from `folder-a.oil` and paste it into `folder-b.oil`:
    - Saving `folder-a` will **delete** the file.
    - Saving `folder-b` will **create** a new file.
    - *Note: This is a "Copy + Delete" operation. File metadata (like Git history or creation dates) will be lost.*

### 2. Deletion Safety
**Is deletion permanent?**
Yes, on the physical disk. However:
- The plugin uses the IntelliJ Virtual File System (VFS), which automatically records every deletion in **Local History**.
- To recover a deleted file: Right-click the parent folder -> **Local History -> Show History**. You can revert the deletion from there.
- Files do **not** go to the OS Trash/Recycle Bin.

### 3. Undo Behavior (`Cmd + Z`)
- **Text Undo:** Pressing `Cmd + Z` in the `.oil` buffer reverts your text edits.
- **Filesystem Undo:** Undo does **not** revert changes already synced to the disk. If you save and then undo, the text will go back to the old state, but the files will remain changed. You would need to save *again* to revert the disk state.

---

## 🛠 Technical Architecture (Coding)

The plugin implements a custom language stack to provide a high-fidelity integrated experience.

### 1. The Language Stack (`OilLanguage.kt`, `OilFileType.kt`)
Instead of plain text, we use a custom `OilLanguage`.
- **FileType:** Registered as `*.oil`. It uses the standard folder icon for tabs.
- **Parser Definition (`OilParserDefinition.kt`):** This is the core of the language integration. It provides a Lexer and Parser that treat each line as a distinct PSI element (`OIL_LINE`). This structured tree is required for the IDE's background processes (highlighting, indexing) to run without crashing.

### 2. Visual Overhaul (`OilAnnotator.kt`)
The "nice" look is provided by a custom Annotator that runs on the PSI tree:
- **Gutter Icons:** Uses `GutterIconRenderer` to look up and display the actual IntelliJ system icon for every file and folder.
- **Thematic Highlighting:**
    - **Directories:** Bold/Blue (Keyword style).
    - **Hidden Files:** Dimmed (Comment style).
    - **Code/Config/Docs:** Different colors based on file extension (Identifier, Metadata, and String styles).

### 3. Synchronization Logic (`OilSaveListener.kt`)
We intercept the document save event to synchronize changes:
1.  **RangeMarkers:** Every line is tracked by a `RangeMarker` that persists even as you edit the text.
2.  **Multi-Pass Sync:**
    - **Rename Pass:** Issues `VirtualFile.rename` for modified lines.
    - **Adoption Pass:** Prevents deletion if a line was deleted and re-typed.
    - **Delete/Create Passes:** Handles removals and new file additions.
3.  **Atomic Operations:** All VFS changes are wrapped in a `WriteCommandAction` for consistency and safety.

---

## 🐛 Troubleshooting

### Why is it still looking like a text file?
Ensure the file has the `.oil` extension. If you are using an old buffer, close it and run "Open Oil" again. The styling requires a proper `OilFile` PSI instance to be created by the IDE.

### ConcurrentModificationException in Build
This is a known intermittent issue during the `buildSearchableOptions` task in some Gradle/IntelliJ environments. It usually doesn't affect the final plugin build. If the build fails, simply run `./gradlew build` again.
