(function (AD) {
  'use strict';

  const esc = AD.esc;
  const icon = AD.icon;

  AD.taskHeader = function (task, project) {
    return '<header class="topbar task-topbar">' +
      '<button class="icon-btn" data-action="back" aria-label="Back">' + icon('chevron-left') + '</button>' +
      '<div class="task-heading"><h1 tabindex="-1" title="' + esc(task.title) + '">' + esc(task.title) + '</h1>' +
      '<div class="task-subline"><span class="task-branch">' + esc(project.name) + ' · ' + esc(task.branch) + '</span>' +
      (AD.isLocalTask(task) ? '<span class="local-chip">' + icon('phone') + 'Local</span>' : '') +
      '<span class="demo-tag">Demo</span></div></div>' +
      '<div class="task-head-status">' + AD.statusPill(task.status) + '</div></header>';
  };

  AD.taskTabs = function (task, active) {
    const tab = function (id, label, count) {
      return '<a class="detail-tab' + (active === id ? ' active' : '') + '" href="#/task/' +
        encodeURIComponent(task.id) + '/' + id + '"' + (active === id ? ' aria-current="page"' : '') + '>' +
        label + '<span class="tab-count">' + count + '</span></a>';
    };
    return '<nav class="detail-tabs" aria-label="Task sections">' +
      tab('chat', 'Chat', task.messages.length) +
      tab('changes', 'Changes', task.changedFiles.length) +
      tab('checks', 'Checks', task.checks.length) +
      '</nav>';
  };

  AD.dayLabel = function (isoDate) {
    const date = new Date(isoDate);
    const today = new Date();
    const sameDay = date.toDateString() === today.toDateString();
    if (sameDay) return 'Today';
    return new Intl.DateTimeFormat('en', { weekday: 'short', month: 'short', day: 'numeric' }).format(date);
  };

  AD.messageMeta = function (task, message) {
    if (message.role !== 'user') return '<span>' + esc(AD.clockTime(message.at)) + '</span>';
    if (message.state === 'sending') {
      return '<span class="msg-state"><span class="spinner tiny"></span>Sending…</span>';
    }
    if (message.state === 'queued') {
      return '<span class="msg-state">' + icon('clock') + 'Queued</span>';
    }
    if (message.state === 'failed') {
      return '<span class="msg-state error">' + icon('alert') + 'Not sent</span>' +
        '<button class="retry-link" data-action="retry-message" data-task="' + esc(task.id) +
        '" data-message="' + esc(message.id) + '">Retry</button>';
    }
    return '<span>' + esc(AD.clockTime(message.at)) + '</span>';
  };

  AD.renderMessage = function (task, message) {
    if (message.role === 'system') {
      return '<div class="system-message">' + esc(message.text) + '</div>';
    }
    if (message.role === 'user') {
      return '<div class="message message-user">' +
        '<div class="message-bubble"><span class="sr-only">You: </span>' + AD.nl2br(message.text) + '</div>' +
        '<div class="message-meta">' + AD.messageMeta(task, message) + '</div></div>';
    }
    return '<div class="message message-agent">' +
      '<div class="message-author">' + esc(AD.agentName(task.agentId)) +
      (AD.isLocalTask(task) ? '<span class="msg-local">' + icon('phone') + 'On this phone</span>' : '') +
      (message.preview ? '<span class="preview-chip">Simulated</span>' : '') +
      '</div>' +
      '<div class="message-text">' + AD.nl2br(message.text) + '</div>' +
      '<div class="message-meta">' + AD.messageMeta(task, message) + '</div></div>';
  };

  AD.planCard = function (task) {
    if (!task.plan || !task.plan.length) return '';
    if (!['pending', 'in_progress', 'needs_input', 'failed'].includes(task.status)) return '';
    const done = task.plan.filter(function (step) { return step.state === 'done'; }).length;
    return '<section class="card plan-card">' +
      '<div class="plan-head"><strong>Progress</strong><span>' + done + ' of ' + task.plan.length + '</span></div>' +
      '<ul class="plan-list">' + task.plan.map(function (step) {
        const stepIcon = step.state === 'done' ? icon('check', 'plan-done') :
          step.state === 'failed' ? icon('alert', 'plan-failed') :
            step.state === 'active' ? icon('spinner', 'plan-active spin') : icon('circle', 'plan-todo');
        return '<li class="' + step.state + '">' + stepIcon + '<span>' + esc(step.text) + '</span></li>';
      }).join('') + '</ul></section>';
  };

  AD.permissionCard = function (task) {
    const request = task.permissionRequest;
    if (!request || request.state !== 'pending') return '';
    return '<section class="card permission-card">' +
      '<div class="card-eyebrow">' + icon('shield') + 'Permission needed</div>' +
      '<h3>' + esc(request.title) + '</h3>' +
      '<p>' + esc(request.body) + '</p>' +
      '<code class="command">' + icon('terminal') + esc(request.command) + '</code>' +
      '<div class="card-actions split">' +
      '<button class="btn btn-outline" data-action="decline-request" data-task="' + esc(task.id) + '"' +
      (AD.canReachHost() ? '' : ' disabled') + '>Decline</button>' +
      '<button class="btn btn-dark" data-action="allow-request" data-task="' + esc(task.id) + '"' +
      (AD.canReachHost() ? '' : ' disabled') + '>Allow once</button></div>' +
      '<p class="fine">Approval applies only to this command in this task.</p></section>';
  };

  AD.readyCard = function (task) {
    if (task.status !== 'review') return '';
    const summary = AD.checkSummary(task.checks);
    const totals = AD.changeTotals(task.changedFiles);
    return '<section class="card ready-card">' +
      '<span class="decision-icon tone-green">' + icon('check-circle') + '</span>' +
      '<div class="ready-copy"><h3>Ready for review</h3>' +
      '<p>' + AD.plural(task.changedFiles.length, 'file') + ' changed · +' + totals.additions + ' −' + totals.deletions +
      '<br>' + esc(summary.label) + '</p></div>' +
      '<button class="btn btn-dark block-btn" data-action="goto-changes" data-task="' + esc(task.id) + '">Review changes</button>' +
      (summary.counts.failed ? '<p class="warn-text">' + icon('alert') + ' A check failed. Review it before approving.</p>' : '') +
      '</section>';
  };

  AD.failureCard = function (task) {
    if (task.status !== 'failed' || !task.failure) return '';
    return '<section class="card failure-card">' +
      '<div class="card-eyebrow danger">' + icon('alert') + 'Task stopped</div>' +
      '<h3>' + esc(task.failure.title) + '</h3>' +
      '<p>' + esc(task.failure.summary) + '</p>' +
      '<pre class="error-output">' + esc(task.failure.detail) + '</pre>' +
      '<div class="card-actions split">' +
      '<button class="btn btn-outline" data-action="goto-checks" data-task="' + esc(task.id) + '">View checks</button>' +
      '<button class="btn btn-dark" data-action="retry-task" data-task="' + esc(task.id) + '"' +
      (AD.canReachHost() ? '' : ' disabled') + '>' + icon('refresh') + 'Retry task</button></div>' +
      (AD.canReachHost() ? '' : '<p class="fine">Reconnect the host before retrying.</p>') + '</section>';
  };

  AD.decisionCard = function (task) {
    const latest = AD.latestDecision(task);
    if (!latest || task.status !== 'completed') return '';
    return '<section class="card decision-card">' +
      '<span class="decision-icon tone-green">' + icon('check') + '</span>' +
      '<div class="decision-copy"><strong>Approved by you</strong>' +
      '<p>' + esc(AD.fullDate(latest.at)) + ' · ' + esc(AD.clockTime(latest.at)) + '</p>' +
      (latest.feedback ? '<p class="decision-feedback">“' + esc(latest.feedback) + '”</p>' : '') + '</div></section>';
  };

  AD.activityCard = function (task) {
    if (!task.activity.length) return '';
    const open = Boolean(AD.state.ui.activityOpen[task.id]);
    const events = open ? task.activity : task.activity.slice(-2);
    return '<section class="card activity-card">' +
      '<button class="activity-head" data-action="toggle-activity" data-task="' + esc(task.id) +
      '" aria-expanded="' + open + '">' +
      '<span class="activity-title">' + icon('activity') + 'Activity<span class="sim-chip">simulated</span></span>' +
      '<span class="activity-more">' + AD.plural(task.activity.length, 'event') + icon('chevron-down', open ? 'flip' : '') + '</span></button>' +
      '<ol class="activity-list">' + events.map(function (event) {
        return '<li>' + icon(AD.ACTIVITY_ICONS[event.type] || 'activity', 'activity-icon') +
          '<span class="activity-text">' + esc(event.text) + '</span>' +
          '<span class="activity-time" data-ts="' + esc(event.at) + '">' + esc(AD.timeAgo(event.at)) + '</span></li>';
      }).join('') + '</ol></section>';
  };

  AD.taskHostBanner = function (task) {
    if (AD.canReachHost()) return '';
    if (AD.isLocalTask(task)) {
      const model = AD.agent(task.agentId);
      const state = AD.modelState(model);
      const copy = model.state === 'ready'
        ? 'Local chat is available on this phone. File changes, checks, and approvals need the host.'
        : model.state === 'loading'
          ? 'The local model is loading. File changes, checks, and approvals still need the host.'
          : 'This local model is ' + state.label.toLowerCase() + '. Load it in Settings to chat on this phone. File changes and checks need the host.';
      return '<div class="banner local">' + icon('phone') +
        '<div class="banner-copy"><strong>Studio Mac is offline</strong>' +
        '<span>' + esc(copy) + '</span></div>' +
        '<button class="banner-action" data-action="reconnect-host"' + (AD.state.ui.connecting ? ' disabled' : '') + '>' +
        (AD.state.ui.connecting ? '<span class="spinner tiny"></span>Reconnecting…' : 'Reconnect') + '</button></div>';
    }
    return AD.hostBanner();
  };

  AD.localTyping = function (task) {
    return '<div class="message message-agent local-typing">' +
      '<div class="message-author">' + esc(AD.agentName(task.agentId)) +
      '<span class="msg-local">' + icon('phone') + 'On this phone</span>' +
      '<span class="preview-chip">Simulated</span></div>' +
      '<div class="typing-bubble"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span>' +
      '<span>Generating locally…</span></div></div>';
  };

  AD.composer = function (task) {
    const local = AD.isLocalTask(task);
    const model = AD.agent(task.agentId);
    if (task.status === 'completed') {
      return '<footer class="composer-wrap"><div class="composer-note done">' + icon('check-circle') +
        '<span>Task completed. Start a new task to keep going.</span>' +
        '<a class="btn btn-outline small" href="#/new">New task</a></div></footer>';
    }
    if (local && model.state !== 'ready') {
      const loading = model.state === 'loading';
      return '<footer class="composer-wrap"><div class="composer-note">' +
        (loading ? '<span class="spinner dark"></span>' : icon('phone')) +
        '<span>' + (loading ? 'Loading ' + esc(model.name) + '…' : 'This local model is ' + esc(AD.modelState(model).label.toLowerCase()) + '. Load it to chat on this phone.') + '</span>' +
        '<button class="btn btn-outline small" data-action="go-settings">Manage models</button></div></footer>';
    }
    if (!local && !AD.canReachHost()) {
      return '<footer class="composer-wrap"><div class="composer-note">' + icon('cloud-off') +
        '<span>Studio Mac is offline. Reconnect to send messages.</span>' +
        '<button class="btn btn-outline small" data-action="reconnect-host">Reconnect</button></div></footer>';
    }
    let placeholder = 'Message the agent…';
    if (local) placeholder = 'Message locally…';
    if (task.status === 'pending') placeholder = 'Add an instruction to the queue…';
    if (task.status === 'needs_input') placeholder = local ? 'Reply locally…' : 'Answer the agent…';
    if (task.status === 'review') placeholder = local ? 'Reply locally…' : 'Reply or request changes…';
    if (task.status === 'failed') placeholder = 'Leave context for the agent…';
    const draft = AD.state.ui.drafts[task.id] || '';
    const typing = Boolean(local && AD.state.ui.localTyping[task.id]);
    const hint = local ?
      (AD.canReachHost() ? 'Runs on this phone. File changes, checks, and approvals still require the host.' : 'Local chat is available. File changes, checks, and approvals need Studio Mac.') :
      task.status === 'pending' ? 'Messages are queued until this task starts.' :
        task.status === 'review' ? 'Sending a message reopens the task for more work.' :
          task.status === 'failed' ? 'This task is stopped. Messages are saved; retry the task to resume.' :
            task.status === 'needs_input' && task.permissionRequest && task.permissionRequest.state === 'pending' ? 'Resolve the permission request above, or reply below.' : '';
    return '<footer class="composer-wrap">' +
      '<form id="composer-form" class="composer-form" data-task="' + esc(task.id) + '">' +
      '<textarea id="composer-input" class="composer-input" rows="1" placeholder="' + esc(placeholder) +
      '" aria-label="Message the agent" enterkeyhint="send">' + esc(draft) + '</textarea>' +
      '<button class="send-btn" type="submit" aria-label="Send message"' +
      (draft.trim() && !typing ? '' : ' disabled') + '>' + (typing ? '<span class="spinner"></span>' : icon('send')) + '</button></form>' +
      '<div class="composer-meta"><span>' + esc(AD.agentName(task.agentId)) + '</span><span class="dot-sep">·</span>' +
      (local ? '<span class="composer-local">' + icon('phone') + 'On this phone</span><span class="dot-sep">·</span>' : '') +
      '<span>Standard permissions</span></div>' +
      (hint ? '<p class="composer-hint">' + esc(hint) + '</p>' : '') + '</footer>';
  };

  AD.chatBody = function (task) {
    const messagesHtml = task.messages.map(function (message) { return AD.renderMessage(task, message); }).join('');
    return '<main id="main" class="screen-body chat-body" data-body="1">' +
      AD.taskHostBanner(task) +
      '<div class="chat-date">' + AD.dayLabel(task.createdAt) + '</div>' +
      '<div class="message-list">' + messagesHtml +
      (AD.state.ui.localTyping[task.id] ? AD.localTyping(task) : '') + '</div>' +
      AD.planCard(task) +
      AD.activityCard(task) +
      AD.permissionCard(task) +
      AD.failureCard(task) +
      AD.readyCard(task) +
      AD.decisionCard(task) +
      '<div class="chat-bottom"></div></main>';
  };

  AD.screenTask = function () {
    const route = AD.state.route;
    const task = AD.getTask(route.params.id);
    if (!task) {
      return '<div class="screen">' +
        '<header class="topbar"><button class="icon-btn" data-action="back" aria-label="Back">' + icon('chevron-left') + '</button>' +
        '<div class="topbar-title"><h1>Task not found</h1></div></header>' +
        '<main id="main" class="screen-body" data-body="1">' +
        AD.emptyState({ icon: 'search', title: 'This task is unavailable', text: 'It may have been removed from this workspace.', action: '<a class="btn btn-dark small" href="#/tasks">Back to tasks</a>' }) +
        '</main></div>';
    }
    const project = AD.getProject(task.projectId);
    const tab = route.params.tab;
    let body;
    if (tab === 'changes') body = AD.changesBody(task, project);
    else if (tab === 'checks') body = AD.checksBody(task);
    else body = AD.chatBody(task);
    const footer = tab === 'chat' ? AD.composer(task) : (tab === 'changes' ? AD.reviewFooter(task) : '');
    return '<div class="screen task-screen">' +
      AD.taskHeader(task, project) +
      AD.taskTabs(task, tab) +
      body + footer + '</div>';
  };
})(window.AD);
