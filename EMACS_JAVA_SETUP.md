# Emacs Java Development Guide: FedProClient-main

This document outlines the options for setting up a modern Java development environment in Emacs for the `FedProClient` project.

## Prerequisites

- **JDK 21**: The project is configured to use Java 21.
- **Gradle**: The project uses Gradle (wrapper is included in `java/gradlew`).
- **Emacs 28+**: (Emacs 29+ recommended for built-in `eglot` and `use-package`).

---

## Option 1: The "Full IDE" Experience (Recommended)

This setup uses `lsp-mode` and `lsp-java`, which provides the most comprehensive feature set, including automatic management of the Eclipse JDT.LS server.

### 1. LSP Setup (`lsp-mode` + `lsp-java`)

`lsp-java` is a bridge to Eclipse JDT.LS. It handles downloading the server and indexing your Gradle project automatically.

**Key Configuration:**
```elisp
(use-package lsp-java
  :ensure t
  :config
  (add-hook 'java-mode-hook #'lsp))

(use-package lsp-mode
  :ensure t
  :hook (java-mode . lsp)
  :commands lsp)

(use-package lsp-ui
  :ensure t
  :commands lsp-ui-mode)

(use-package lsp-treemacs
  :ensure t
  :commands lsp-treemacs-errors-list)
```

### 2. Debugging (`dap-mode`)

`dap-mode` works seamlessly with `lsp-java` to provide a full-featured debugger (breakpoints, stack traces, variable inspection).

**Key Configuration:**
```elisp
(use-package dap-mode
  :ensure t
  :after lsp-mode
  :config
  (dap-mode 1)
  (dap-ui-mode 1)
  (dap-tooltip-mode 1)
  (dap-ui-controls-mode 1)
  (require 'dap-java))
```

**Usage:**
- `M-x dap-debug`: Select a configuration (it should automatically detect Gradle main classes or tests).
- Breakpoints: `M-x dap-breakpoint-add`.

---

## Option 2: The Lightweight/Modern Approach

This setup uses `eglot` (built into Emacs 29) and `dape`. It's faster and follows Emacs' "built-in first" philosophy.

### 1. LSP Setup (`eglot`)

For an **offline setup**, you have extracted JDTLS to `~/.emacs.d/bin/jdtls`.

**To configure Eglot to use the built-in JDTLS binary:**
```elisp
(use-package eglot
  :ensure t
  :config
  (add-to-list 'eglot-server-programs
               `(java-mode . ("/home/jdyer/.emacs.d/bin/jdtls/bin/jdtls"))))

(add-hook 'java-mode-hook 'eglot-ensure)
```

**Note**: The `bin/jdtls` script is a Python-based launcher that automatically identifies the correct launcher JAR and configuration for your system.

---

## Project-Specific Tips for FedProClient

### Multi-Module Import
When you first open a Java file in `java/client` or `java/api`, `lsp-java` will ask to import the project. Point it to the `java/` directory (where `settings.gradle.kts` is) to ensure all modules are indexed together.

### Troubleshooting
- **Memory**: JDT.LS can be memory-intensive. You can increase the heap size in `lsp-java`:
  ```elisp
  (setq lsp-java-vmargs '("-Xmx4G" "-XX:+UseG1GC" "-XX:+UseStringDeduplication"))
  ```
- **Cleaning Workspace**: If the LSP gets confused, run `M-x lsp-java-update-project-config` or manually delete the `.lsp-java-workspace` directory in your home.

### Build Integration
Use `compile-command` or a package like `prodigy` or `projectile` to run `./gradlew build` directly from Emacs.
```elisp
(setq compile-command "./gradlew build")
```
