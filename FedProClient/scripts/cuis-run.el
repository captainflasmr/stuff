;;; cuis-run.el --- Bring up the full CUIS / pRTI stack from Emacs -*- lexical-binding: t; -*-

;; Drives the start-*.sh scripts that live next to this file, each in its own
;; `term' buffer.  The scripts wait on their upstream ports, so the stack comes
;; up in the right order no matter how fast the buffers are launched.
;;
;; Usage:
;;   (load "/path/to/java/scripts/cuis-run.el")
;;   M-x cuis-system-start     ; CRC, FedPro, CUIS, Sub-App, publisher — tiled
;;   M-x cuis-system-stop      ; Ctrl-C every process (reverse order)
;;   M-x cuis-system-status    ; run status.sh in a buffer
;; Individual: cuis-start-crc / -fedpro / -cuis-server / -subapp / -publisher

;;; Code:

(require 'term)

(defvar cuis-scripts-dir
  (file-name-directory (or load-file-name buffer-file-name default-directory))
  "Directory holding the CUIS start-*.sh scripts (this file's directory).")

;; Process name -> script, in startup/display order.  The Sub-App is started
;; before the publisher so its subscription is in place for the first updates.
(defconst cuis--processes
  '(("crc"         . "start-crc.sh")
    ("fedpro"      . "start-fedpro.sh")
    ("cuis-server" . "start-cuis-server.sh")
    ("subapp"      . "start-subapp.sh")
    ("publisher"   . "start-publisher.sh"))
  "Alist of (NAME . SCRIPT) for each process in the stack.")

(defun cuis--buffer-name (name) (format "*cuis:%s*" name))

(defun cuis--term (name script)
  "Run SCRIPT in a dedicated `term' buffer for process NAME; return the buffer."
  (let* ((bufname (cuis--buffer-name name))
         (path    (expand-file-name script cuis-scripts-dir))
         (buf     (get-buffer bufname)))
    (when (and buf (term-check-proc buf))
      (user-error "%s is already running (M-x cuis-system-stop first)" bufname))
    (unless (file-exists-p path)
      (user-error "Script not found: %s (set `cuis-scripts-dir')" path))
    (setq buf (get-buffer-create bufname))
    (with-current-buffer buf
      (unless (derived-mode-p 'term-mode) (term-mode))
      (term-exec buf bufname "/bin/bash" nil (list path))
      (term-char-mode))
    buf))

;;;###autoload
(defun cuis-start-crc ()         (interactive) (cuis--term "crc"         "start-crc.sh"))
;;;###autoload
(defun cuis-start-fedpro ()      (interactive) (cuis--term "fedpro"      "start-fedpro.sh"))
;;;###autoload
(defun cuis-start-cuis-server () (interactive) (cuis--term "cuis-server" "start-cuis-server.sh"))
;;;###autoload
(defun cuis-start-subapp ()      (interactive) (cuis--term "subapp"      "start-subapp.sh"))
;;;###autoload
(defun cuis-start-publisher ()   (interactive) (cuis--term "publisher"   "start-publisher.sh"))

(defun cuis--largest-window ()
  "Return the live window with the greatest pixel area."
  (car (sort (window-list nil 'no-minibuffer)
             (lambda (a b)
               (> (* (window-pixel-width a) (window-pixel-height a))
                  (* (window-pixel-width b) (window-pixel-height b)))))))

(defun cuis--tile (buffers)
  "Tile BUFFERS one-per-window by repeatedly splitting the largest window
along its longer side — a balanced grid for any count."
  (delete-other-windows)
  (set-window-buffer (selected-window) (car buffers))
  (dolist (buf (cdr buffers))
    (let* ((win (cuis--largest-window))
           (new (with-selected-window win
                  (if (> (window-pixel-width win) (window-pixel-height win))
                      (split-window-right)
                    (split-window-below)))))
      (set-window-buffer new buf)))
  (balance-windows))

;;;###autoload
(defun cuis-system-start ()
  "Start the whole CUIS stack — CRC, Federate Protocol Server, CUIS proxy,
Sub-App subscriber, and publisher — each in its own tiled `term' window.
Each process waits on its upstream port, so launch order is self-correcting."
  (interactive)
  (let ((buffers (mapcar (lambda (p) (cuis--term (car p) (cdr p)))
                         cuis--processes)))
    (cuis--tile buffers)
    (select-window (get-buffer-window (car buffers)))
    (message "CUIS stack starting — processes self-sequence on their ports; watch the windows.")))

;;;###autoload
(defun cuis-system-stop ()
  "Send Ctrl-C (SIGINT) to each CUIS process, in reverse startup order."
  (interactive)
  (dolist (name (reverse (mapcar #'car cuis--processes)))
    (let* ((buf  (get-buffer (cuis--buffer-name name)))
           (proc (and buf (get-buffer-process buf))))
      (when (process-live-p proc)
        (interrupt-process proc))))
  (message "Sent Ctrl-C to CUIS processes. Buffers remain; M-x cuis-system-kill to remove them."))

;;;###autoload
(defun cuis-system-kill ()
  "Kill every CUIS process and its buffer."
  (interactive)
  (dolist (name (mapcar #'car cuis--processes))
    (let ((buf (get-buffer (cuis--buffer-name name))))
      (when buf
        (let ((kill-buffer-query-functions nil))
          (kill-buffer buf)))))
  (message "CUIS buffers killed."))

;;;###autoload
(defun cuis-system-status ()
  "Run status.sh and show which tiers are up in a buffer."
  (interactive)
  (let ((default-directory cuis-scripts-dir))
    (with-current-buffer (get-buffer-create "*cuis:status*")
      (erase-buffer)
      (call-process "/bin/bash" nil t t
                    (expand-file-name "status.sh" cuis-scripts-dir))
      (display-buffer (current-buffer)))))

(provide 'cuis-run)
;;; cuis-run.el ends here
