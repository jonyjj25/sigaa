case "$*" in
  *"config --format json"*)
    printf '{"services":{"api":{"volumes":[{"target":"/var/lib/sigaa/backups-external","source":"%s"}]}}}' "$FAKE_EXTERNAL";;
  *"config --quiet"*) exit 0;;
  *"ps --services --status running"*) printf '%s\n' "$FAKE_RUNNING";;
  *"stop -t 120"*) echo stop >> "$FAKE_LOG";;
  *" start "*) echo start >> "$FAKE_LOG";;
  *" run "*)
    echo create >> "$FAKE_LOG"
    if [[ "$FAKE_FAILURE" == yes ]]; then exit 12; fi
    if [[ "$FAKE_FAILURE" == signal ]]; then kill -TERM "$PPID"; exit 143; fi
    name="${!#}"
    mkdir -p "$FAKE_EXTERNAL/full"
    printf fixture > "$FAKE_EXTERNAL/full/${name##*/}";;
  *) echo 'Unexpected fake Docker command' >&2; exit 90;;
esac
