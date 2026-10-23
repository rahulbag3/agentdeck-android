(function (AD) {
  'use strict';

  const esc = AD.esc;
  const icon = AD.icon;

  AD.changesBody = function (task, project) {
    const selected = task.changedFiles.find(function (file) { return file.id === AD.state.ui.diffFileId; });
    if (selected) return AD.diffBody(task, project, selected);
    const files = task.changedFiles;
    const totals = AD.changeTotals(files);
    const summary = AD.checkSummary(task.checks);
    const chatOnly = AD.isLocalTask(task) && !files.length;
    const readiness = task.status === 'review' ? 'Review additions and deletions, then approve the work or request changes.' :
      task.status === 'completed' ? 'This work was approved. The preview stays available in history.' :
        task.status === 'failed' ? 'This task stopped before finishing. Review the changes, then retry.' :
          task.status === 'needs_input' ? 'Waiting for your response before work continues.' :
            task.status === 'pending' ? 'The task is queued. Files will appear when execution starts.' :
              'The agent is still working. Files may change until it finishes.';
    let content;
    if (!files.length) {
      const waiting = ['pending', 'in_progress', 'needs_input'].includes(task.status);
      const local = AD.isLocalTask(task);
      content = AD.emptyState({
        icon: local ? 'phone' : 'file',
        title: local ? 'Local chat only' : (waiting ? 'No changes yet' : 'No changed files'),
        text: local ? 'This task is scoped to a local explanation. File changes require the execution host.' :
          waiting ? 'Files will appear here as the agent edits the project.' :
            'The host did not report any changed files for this task.',
        action: '<a class="btn btn-outline small" href="#/task/' + encodeURIComponent(task.id) + '/chat">Back to chat</a>'
      });
    } else {
      content = '<div class="file-list">' + files.map(function (file) {
        const meta = AD.FILE_META[file.status] || AD.FILE_META.modified;
        const stats = AD.fileStats(file);
        return '<button class="file-row" data-action="open-diff" data-task="' + esc(task.id) + '" data-file="' + esc(file.id) + '" title="' + esc(file.path) + '">' +
          '<span class="file-badge tone-' + meta.tone + '" aria-hidden="true">' + meta.letter + '</span>' +
          '<span class="file-main"><span class="file-path">' + esc(file.path) + '</span>' +
          '<span class="file-meta">' + meta.label + (file.oldPath ? ' from ' + esc(file.oldPath) : '') + ' · ' + esc(file.language) +
          (file.unavailable ? ' · diff unavailable' : '') + '</span></span>' +
          '<span class="file-counts"><span class="add-count">+' + stats.additions + '</span><span class="del-count">−' + stats.deletions + '</span></span>' +
          '</button>';
      }).join('') + '</div>';
    }
    return '<main id="main" class="screen-body" data-body="1">' +
      AD.taskHostBanner(task) +
      (chatOnly ? '' :
        '<section class="diff-overview">' +
        '<div class="diff-overview-top"><span class="branch-chip">' + icon('branch') + esc(task.branch) + '</span>' +
        '<span class="from-branch">from ' + esc(project.defaultBranch) + '</span></div>' +
        '<div class="diff-total-row"><strong>' + AD.plural(files.length, 'file') + ' changed</strong>' +
        '<span class="add-count">+' + totals.additions + '</span><span class="del-count">−' + totals.deletions + '</span></div>' +
        '<div class="diff-checkline">' +
        (task.checks.length ? '<span class="check-badge tone-' + summary.tone + '">' + icon(summary.counts.failed ? 'x-circle' : 'check-circle') + esc(summary.label) + '</span>' :
          '<span class="check-badge tone-gray">' + icon('circle') + 'No checks reported</span>') +
        '<a class="inline-link" href="#/task/' + encodeURIComponent(task.id) + '/checks">View checks</a></div>' +
        '</section>') +
      content +
      (chatOnly ? '' : '<div class="readiness-note">' + icon('info') + '<p>' + readiness + '</p></div>') +
      '<div class="bottom-space"></div></main>';
  };

  AD.diffBody = function (task, project, file) {
    const meta = AD.FILE_META[file.status] || AD.FILE_META.modified;
    const stats = AD.fileStats(file);
    const options = task.changedFiles.map(function (item) {
      return '<option value="' + esc(item.id) + '"' + (item.id === file.id ? ' selected' : '') + '>' + esc(item.path) + '</option>';
    }).join('');
    let diffContent;
    if (!file.diff) {
      diffContent = '<div class="diff-unavailable">' + icon('file-minus') +
        '<h3>Diff unavailable</h3><p>' + esc(file.unavailableReason || 'The host did not provide a diff for this file.') + '</p>' +
        '<p class="fine">Reported change: +' + stats.additions + ' −' + stats.deletions + '</p></div>';
    } else {
      const oldRows = file.diff.filter(function (row) { return row.old != null; });
      const newRows = file.diff.filter(function (row) { return row.new != null; });
      const firstOld = oldRows.length ? oldRows[0].old : 0;
      const firstNew = newRows.length ? newRows[0].new : 0;
      const lines = file.diff.map(function (row) {
        const sign = row.type === '+' ? '+' : row.type === '-' ? '−' : ' ';
        return '<div class="diff-line ' + (row.type === '+' ? 'add' : row.type === '-' ? 'del' : 'ctx') + '">' +
          '<span class="line-num old">' + (row.old == null ? '' : row.old) + '</span>' +
          '<span class="line-num new">' + (row.new == null ? '' : row.new) + '</span>' +
          '<span class="line-sign">' + sign + '</span>' +
          '<span class="line-code">' + (row.text ? esc(row.text) : '&nbsp;') + '</span></div>';
      }).join('');
      diffContent = '<div class="diff-scroll" tabindex="0" role="region" aria-label="Diff for ' + esc(file.path) + '">' +
        '<div class="diff-table">' +
        '<div class="diff-hunk">@@ −' + firstOld + ',' + oldRows.length + ' +' + firstNew + ',' + newRows.length + ' @@</div>' +
        lines + '</div></div>' +
        '<p class="scroll-hint">' + icon('arrow-right') + 'Long lines scroll sideways. This diff is read-only.</p>';
    }
    return '<main id="main" class="screen-body" data-body="1">' +
      AD.taskHostBanner(task) +
      '<div class="diff-toolbar">' +
      '<button class="back-link" data-action="close-diff">' + icon('chevron-left') + 'All files</button>' +
      '<label class="file-select"><span class="sr-only">Switch file</span><select id="diff-file-select">' + options + '</select>' + icon('chevron-down') + '</label>' +
      '</div>' +
      '<section class="diff-file-head">' +
      '<div class="diff-file-title"><span class="file-badge tone-' + meta.tone + '">' + meta.letter + '</span>' +
      '<div><h2 title="' + esc(file.path) + '">' + esc(file.path) + '</h2>' + (file.oldPath ? '<p class="renamed-from">Renamed from ' + esc(file.oldPath) + '</p>' : '') +
      '<p class="diff-file-sub">' + meta.label + ' · ' + esc(file.language) + ' · +' + stats.additions + ' −' + stats.deletions + '</p></div>' +
      '<button class="icon-btn tiny" data-action="copy-path" data-path="' + esc(file.path) + '" aria-label="Copy file path">' + icon('copy') + '</button></div>' +
      '<div class="diff-file-meta">' + icon('branch') + esc(task.branch) + ' · from ' + esc(project.defaultBranch) + '</div>' +
      '</section>' +
      diffContent +
      '<div class="bottom-space"></div></main>';
  };

  AD.reviewFooter = function (task) {
    if (task.status !== 'review') return '';
    const online = AD.canReachHost();
    const summary = AD.checkSummary(task.checks);
    return '<footer class="review-bar">' +
      '<div class="review-bar-meta"><span>' + AD.plural(task.changedFiles.length, 'file') + ' · ' + esc(summary.label) + '</span>' +
      (online ? '' : '<span class="offline-inline">' + icon('cloud-off') + 'Offline</span>') + '</div>' +
      '<div class="review-bar-actions">' +
      '<button class="btn btn-outline" data-action="request-changes" data-task="' + esc(task.id) + '"' + (online ? '' : ' disabled') + '>Request changes</button>' +
      '<button class="btn btn-dark" data-action="approve" data-task="' + esc(task.id) + '"' + (online ? '' : ' disabled') + '>' + icon('check') + 'Approve</button></div>' +
      (online ? '' : '<p class="fine center">Reconnect the host to record a decision.</p>') +
      '</footer>';
  };

  AD.checksBody = function (task) {
    const checks = task.checks;
    const summary = AD.checkSummary(checks);
    let content;
    if (!checks.length) {
      const waiting = ['pending', 'in_progress', 'needs_input'].includes(task.status);
      const local = AD.isLocalTask(task);
      content = AD.emptyState({
        icon: local ? 'phone' : 'terminal',
        title: local ? 'No checks were run' : (waiting ? 'Checks have not run yet' : 'No checks were reported'),
        text: local ? 'This local task did not request execution. Checks run on the execution host.' :
          waiting ? 'The agent runs the configured checks before asking for review. Nothing has been reported yet.' :
            'Without validation, do not treat this work as verified. You can ask the agent to run checks.',
        action: '<a class="btn btn-outline small" href="#/task/' + encodeURIComponent(task.id) + '/chat">Open chat</a>'
      });
    } else {
      content = '<div class="checks-list">' + checks.map(function (check) {
        const meta = AD.CHECK_META[check.status] || AD.CHECK_META.not_run;
        return '<section class="check-card tone-' + meta.tone + '">' +
          '<div class="check-head"><span class="check-icon tone-' + meta.tone + '">' + icon(meta.icon) + '</span>' +
          '<div class="check-name"><strong>' + esc(check.name) + '</strong>' +
          (check.command && check.command !== '—' ? '<span class="check-command">' + esc(check.command) + '</span>' : '') + '</div>' +
          '<span class="check-pill tone-' + meta.tone + '">' + meta.label + '</span></div>' +
          '<p class="check-summary">' + esc(check.summary) + (check.duration ? ' · ' + esc(check.duration) : '') + '</p>' +
          (check.output ? '<details class="check-output"' + (check.status === 'failed' ? ' open' : '') + '>' +
            '<summary>' + (check.status === 'failed' ? 'View error output' : 'View output') + '</summary>' +
            '<pre>' + esc(check.output) + '</pre></details>' : '') +
          (check.status === 'failed' && task.status !== 'completed' ? '<button class="inline-link strong ask-check" data-action="ask-check" data-task="' + esc(task.id) + '" data-check="' + esc(check.id) + '">' +
            icon('message') + 'Ask the agent about this</button>' : '') +
          '</section>';
      }).join('') + '</div>';
    }
    return '<main id="main" class="screen-body" data-body="1">' +
      AD.taskHostBanner(task) +
      (checks.length ? '<section class="checks-summary tone-' + summary.tone + '">' +
        '<div class="checks-summary-icon">' + icon(summary.counts.failed ? 'x-circle' : summary.counts.passed ? 'check-circle' : 'terminal') + '</div>' +
        '<div class="checks-summary-copy"><strong>' + esc(summary.label) + '</strong>' +
        '<p>' + summary.counts.passed + ' passed · ' + summary.counts.failed + ' failed · ' +
        summary.counts.skipped + ' skipped · ' + summary.counts.not_run + ' not run</p></div>' +
        '<span class="sim-chip">simulated</span></section>' : '') +
      content +
      (checks.length ? '<div class="readiness-note">' + icon('info') +
        '<p>Only checks configured for this project are shown. A skipped or not-run check is not a pass.</p></div>' : '') +
      '<div class="bottom-space"></div></main>';
  };
})(window.AD);
