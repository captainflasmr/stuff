#
# ~/.bashrc
#

# Add this lines at the top of .bashrc:
# [[ $- == *i* ]] && source /usr/share/blesh/ble.sh --noattach

# If not running interactively, don't do anything
[[ $- != *i* ]] && return

export HISTCONTROL=ignoreboth:erasedups

PS1='[\u:\w]\$ '

if [ -d "$HOME/.bin" ] ;
  then PATH="$HOME/.bin:$PATH"
fi

if [ -d "$HOME/.local/bin" ] ;
  then PATH="$HOME/.local/bin:$PATH"
fi

#ignore upper and lowercase when TAB completion
bind 'set completion-ignore-case on'
bind 'set show-all-if-ambiguous on'
bind 'TAB:menu-complete'

if [ -d "$HOME/bin" ] ;
  then PATH="$HOME/bin:$PATH"
fi

if [ -d "$HOME/bin/bootstrap" ] ; then
  case ":$PATH:" in
    *":$HOME/bin/bootstrap:"*) ;;
    *) PATH="$HOME/bin/bootstrap:$PATH" ;;
  esac
fi

# Replace ls with exa
alias l='exa -lah --color=always --group-directories-first --icons' # tree listing
export OLLAMA_API_BASE_URL=172.17.0.1
# SSH agent from GNOME keyring (gcr) — set by PAM in desktop sessions,
# but terminals launched outside the desktop may need an explicit path.
if [ -z "$SSH_AUTH_SOCK" ] && [ -S "${XDG_RUNTIME_DIR}/gcr/ssh" ]; then
  export SSH_AUTH_SOCK="${XDG_RUNTIME_DIR}/gcr/ssh"
fi

# Add this line at the end of .bashrc:
# [[ ${BLE_VERSION-} ]] && ble-attach

# Added by LM Studio CLI (lms)
export PATH="$PATH:/home/jdyer/.lmstudio/bin"

### MANAGED BY RANCHER DESKTOP START (DO NOT EDIT)
export PATH="/home/jdyer/.rd/bin:$PATH"
### MANAGED BY RANCHER DESKTOP END (DO NOT EDIT)
export PATH="$PATH:$HOME/VSCode-linux-x64/bin"

export EDITOR=/usr/bin/emacs

# Flyline - enhanced Bash experience
enable flyline 2>/dev/null || enable -f "/home/jdyer/.local/lib/libflyline.so" flyline

# Flyline Emacs-style navigation (mimic Up/Down/Enter/Escape)
if enable 2>/dev/null | grep -q flyline; then
  flyline key remap Ctrl+P Up
  flyline key remap Ctrl+N Down
  flyline key remap Ctrl+B Left
  flyline key remap Ctrl+F Right
  flyline key bind Ctrl+j tabCompletionEntrySelected=tabCompletionAcceptEntry
  flyline key bind Ctrl+m tabCompletionEntrySelected=tabCompletionAcceptEntry
  flyline key bind Ctrl+g tabCompletionAvailable=escapeToNormalMode
  flyline key bind Ctrl+g fuzzyHistorySearch=escapeToNormalMode
fi
