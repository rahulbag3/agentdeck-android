(function (AD) {
  'use strict';

  const esc = AD.esc;
  const icon = AD.icon;
  let backNavigation = false;

  function sheetElement() {
    return document.getElementById('sheet');
  }

  function navigate(hash) {
    if (location.hash === hash) {
      AD.render();
      return;
    }
    location.hash = hash;
  }

  function goBack() {
    if (AD.state.depth > 0) {
      backNavigation = true;
      history.back();
    } else {
      navigate('#/home');
    }
  }

  function currentTask() {
    if (AD.state.route.name !== 'task') return null;
    return AD.getTask(AD.state.route.params.id);
  }

  /* ---------- sheets ---------- */

  function sheetHeader(title, subtitle) {
    return '<div class="sheet-grab"></div>' +
      '<div class="sheet-head"><div><h2 id="sheet-title" tabindex="-1">' + esc(title) + '</h2>' +
      (subtitle ? '<p>' + subtitle + '</p>' : '') + '</div>' +
      '<button class="icon-btn" data-sheet-action="close" aria-label="Close">' + icon('x') + '</button></div>';
  }

  function sheetHTML() {
    const sheet = AD.state.ui.sheet;
    if (!sheet) return '';
    if (sheet.type === 'projects') {
      const host = AD.state.data.host;
      const rows = AD.state.data.projects.map(function (project) {
        const tasks = AD.tasksForProject(project.id);
        const counts = AD.statusCounts(tasks);
        const open = tasks.length - counts.completed;
        const active = project.id === AD.state.data.settings.activeProjectId;
        return '<button class="sheet-project' + (active ? ' active' : '') + '" data-sheet-action="select-project" data-project="' + esc(project.id) + '">' +
          '<span class="project-mark">' + icon('folder') + '</span>' +
          '<span class="sheet-project-main"><strong>' + esc(project.name) + '</strong>' +
          '<span>' + esc(project.repo) + ' · ' + esc(project.defaultBranch) + '</span>' +
          '<span class="sheet-project-stats">' + AD.plural(open, 'open task') + ' · ' + counts.review + ' in review</span></span>' +
          (active ? '<span class="sheet-check">' + icon('check') + '</span>' : icon('chevron-right')) +
          '</button>';
      }).join('');
      return '<div class="sheet-inner">' + sheetHeader('Switch project', 'Tasks are scoped to one project at a time.') +
        '<div class="sheet-host">' + icon('server') + esc(host.name) + '<span class="dot-inline ' + (AD.canReachHost() ? 'online' : 'offline') + '"></span>' +
        (AD.canReachHost() ? 'Connected' : 'Offline') + '</div>' +
        '<div class="sheet-project-list">' + rows + '</div>' +
        '<div class="sheet-actions single"><button class="btn btn-outline" data-sheet-action="manage-projects">Manage projects</button></div>' +
        '</div>';
    }
    if (sheet.type === 'demo') {
      return '<div class="sheet-inner">' + sheetHeader('About this demo', 'AgentDeck MVP prototype · Milestone 0') +
        '<div class="demo-points">' +
        '<div class="demo-point"><span class="demo-point-icon tone-blue">' + icon('server') + '</span><div><strong>Simulated host</strong><p>Studio Mac is not real. No machine is contacted and no code is executed.</p></div></div>' +
        '<div class="demo-point"><span class="demo-point-icon tone-violet">' + icon('message') + '</span><div><strong>Scripted agent</strong><p>Messages, status changes, files, diffs, and checks are generated locally to demonstrate the workflow.</p></div></div>' +
        '<div class="demo-point"><span class="demo-point-icon tone-amber">' + icon('shield') + '</span><div><strong>Approvals are local</strong><p>Approving records a decision in this browser. Nothing is merged, pushed, or deployed.</p></div></div>' +
        '<div class="demo-point"><span class="demo-point-icon tone-green">' + icon('phone') + '</span><div><strong>On-device preview</strong><p>Local model states and replies are simulated here. The native build will load GGUF models and run inference on the phone.</p></div></div>' +
        '</div>' +
        '<p class="sheet-fine">Data is stored on this device. Reset it from Settings at any time.</p>' +
        '<div class="sheet-actions"><button class="btn btn-outline" data-sheet-action="close">Close</button>' +
        '<button class="btn btn-dark" data-sheet-action="dismiss-demo">Dismiss notice</button></div></div>';
    }
    if (sheet.type === 'approve') {
      const task = AD.getTask(sheet.taskId);
      if (!task) return '';
      const project = AD.getProject(task.projectId);
      const totals = AD.changeTotals(task.changedFiles);
      const summary = AD.checkSummary(task.checks);
      const online = AD.canReachHost();
      return '<div class="sheet-inner">' + sheetHeader('Approve changes?', 'This records your decision in the task history.') +
        '<div class="sheet-summary">' +
        '<div><span>Task</span><strong>' + esc(task.title) + '</strong></div>' +
        '<div><span>Branch</span><strong class="mono">' + esc(task.branch) + ' → ' + esc(project.defaultBranch) + '</strong></div>' +
        '<div><span>Changes</span><strong>' + AD.plural(task.changedFiles.length, 'file') + ' · <span class="add-count">+' + totals.additions + '</span> <span class="del-count">−' + totals.deletions + '</span></strong></div>' +
        '<div><span>Checks</span><strong class="tone-text-' + summary.tone + '">' + esc(summary.label) + '</strong></div>' +
        '</div>' +
        (summary.counts.failed ? '<div class="sheet-warning">' + icon('alert') + '<p>A check failed. Approval will not run checks; review the failure first.</p></div>' : '') +
        (summary.counts.skipped || summary.counts.not_run ? '<p class="sheet-fine">Some checks were skipped or not run. They are not treated as passes.</p>' : '') +
        '<p class="sheet-body-copy">Approving does not merge, push, or deploy. It marks the work as accepted in this demo and unlocks a completed task record.</p>' +
        (online ? '' : '<div class="sheet-warning">' + icon('cloud-off') + '<p>Studio Mac is offline. Reconnect before recording a decision.</p></div>') +
        (sheet.error ? '<div class="sheet-error" role="alert">' + icon('alert') + '<span>' + esc(sheet.error) + '</span></div>' : '') +
        '<label class="check-confirm"><input type="checkbox" id="approve-reviewed"' + (sheet.checked ? ' checked' : '') + '>' +
        '<span>I have reviewed the changes and want to approve them.</span></label>' +
        '<div class="sheet-actions"><button class="btn btn-outline" data-sheet-action="close">Cancel</button>' +
        '<button class="btn btn-dark" data-sheet-action="approve-confirm"' + (!online || !sheet.checked ? ' disabled' : '') + '>' + icon('check') + 'Approve work</button></div>' +
        '</div>';
    }
    if (sheet.type === 'request') {
      const task = AD.getTask(sheet.taskId);
      if (!task) return '';
      const online = AD.canReachHost();
      return '<div class="sheet-inner">' + sheetHeader('Request changes', 'Feedback reopens the task and is recorded in history.') +
        '<label class="sheet-label" for="request-feedback">What should change?</label>' +
        '<textarea class="input textarea sheet-textarea" id="request-feedback" rows="4" placeholder="Be specific: files, behavior, and how to verify the fix.">' + esc(sheet.feedback) + '</textarea>' +
        (online ? '' : '<div class="sheet-warning">' + icon('cloud-off') + '<p>Studio Mac is offline. Reconnect before sending feedback.</p></div>') +
        (sheet.error ? '<div class="sheet-error" role="alert">' + icon('alert') + '<span>' + esc(sheet.error) + '</span></div>' : '') +
        '<p class="sheet-fine">The agent will pick this up and return the task to review when the changes are ready.</p>' +
        '<div class="sheet-actions"><button class="btn btn-outline" data-sheet-action="close">Cancel</button>' +
        '<button class="btn btn-dark" data-sheet-action="request-submit"' + (!online || !sheet.feedback.trim() ? ' disabled' : '') + '>' + icon('send') + 'Send feedback</button></div>' +
        '</div>';
    }
    if (sheet.type === 'models') {
      const draft = AD.getNewTaskDraft();
      const selectedId = draft.agentId;
      const renderModel = function (model) {
        const local = model.provider === 'local';
        const state = AD.modelState(model);
        const unavailable = local && model.state !== 'ready';
        const selected = model.id === selectedId;
        return '<button type="button" class="model-row' + (selected ? ' selected' : '') + (unavailable ? ' unavailable' : '') +
          '" data-sheet-action="select-model" data-model="' + esc(model.id) + '"' + (unavailable ? ' disabled' : '') +
          ' aria-pressed="' + selected + '">' +
          '<span class="model-row-icon ' + (local ? 'local' : 'host') + '">' + icon(local ? 'phone' : 'server') + '</span>' +
          '<span class="model-row-main"><span class="model-row-name">' + esc(model.name) + '</span>' +
          '<span class="model-row-meta">' + esc(AD.modelMeta(model)) + '</span>' +
          '<span class="model-row-state tone-' + state.tone + '">' + esc(local ? state.label : 'Host') + '</span></span>' +
          (selected ? '<span class="model-row-check">' + icon('check') + '</span>' : '') +
          '</button>';
      };
      return '<div class="sheet-inner">' + sheetHeader('Choose a model', 'Local chat runs on this phone. Execution stays with the host.') +
        '<div class="model-sheet-section"><div class="model-section-label">On this device<span class="preview-pill">Simulated</span></div>' +
        AD.localModels().map(renderModel).join('') +
        '</div>' +
        '<div class="model-sheet-section"><div class="model-section-label">Host models</div>' +
        AD.hostModels().map(renderModel).join('') +
        '</div>' +
        '<p class="sheet-fine">Web preview: on-device inference is simulated. The native build will run GGUF models on this phone. File changes, checks, and approvals still use the execution host.</p>' +
        '<div class="sheet-actions single"><button class="btn btn-outline" data-sheet-action="manage-models">Manage local models</button></div>' +
        '</div>';
    }
    if (sheet.type === 'confirm') {
      return '<div class="sheet-inner">' + sheetHeader(sheet.title, '') +
        '<p class="sheet-body-copy">' + esc(sheet.body) + '</p>' +
        (sheet.error ? '<div class="sheet-error">' + icon('alert') + '<span>' + esc(sheet.error) + '</span></div>' : '') +
        '<div class="sheet-actions"><button class="btn btn-outline" data-sheet-action="close">Cancel</button>' +
        '<button class="btn ' + (sheet.danger ? 'btn-danger' : 'btn-dark') + '" data-sheet-action="confirm">' + esc(sheet.confirmLabel) + '</button></div>' +
        '</div>';
    }
    return '';
  }

  function renderSheet() {
    const element = sheetElement();
    if (!element) return;
    const active = document.activeElement;
    const focusId = active && active.id && element.contains(active) ? active.id : null;
    const caret = active && typeof active.selectionStart === 'number' ? active.selectionStart : null;
    element.innerHTML = sheetHTML();
    if (focusId) {
      const target = document.getElementById(focusId);
      if (target) {
        target.focus({ preventScroll: true });
        if (caret != null && target.setSelectionRange) {
          try { target.setSelectionRange(caret, caret); } catch (error) { /* ignored */ }
        }
      }
    }
    if (!element.open) {
      element.showModal();
      document.body.classList.add('sheet-open');
      const preferred = element.querySelector('#request-feedback');
      if (preferred) {
        preferred.focus();
        preferred.setSelectionRange(preferred.value.length, preferred.value.length);
      } else {
        const heading = element.querySelector('#sheet-title');
        if (heading) heading.focus();
      }
    }
  }

  function openSheet(sheet) {
    AD.state.ui.sheet = sheet;
    renderSheet();
  }

  function closeSheet() {
    const element = sheetElement();
    if (element && element.open) element.close();
    AD.state.ui.sheet = null;
    document.body.classList.remove('sheet-open');
  }

  function openProjectSheet() {
    openSheet({ type: 'projects' });
  }

  function openApproveSheet(taskId) {
    openSheet({ type: 'approve', taskId: taskId, checked: false, error: '' });
  }

  function openRequestSheet(taskId) {
    openSheet({ type: 'request', taskId: taskId, feedback: '', error: '' });
  }

  function openConfirm(options) {
    openSheet({
      type: 'confirm',
      title: options.title,
      body: options.body,
      confirmLabel: options.confirmLabel,
      confirmAction: options.confirmAction,
      danger: Boolean(options.danger),
      error: ''
    });
  }

  function handleSheetAction(action, button) {
    const sheet = AD.state.ui.sheet;
    if (!sheet) return;
    if (action === 'close') {
      closeSheet();
      return;
    }
    if (action === 'select-project') {
      const project = AD.getProject(button.getAttribute('data-project'));
      if (!project) return;
      AD.state.data.settings.activeProjectId = project.id;
      AD.state.data.settings.defaultProjectId = project.id;
      AD.state.ui.statusFilter = 'all';
      AD.state.ui.taskSearch = '';
      AD.state.ui.homeSearch = '';
      AD.save();
      closeSheet();
      AD.render();
      AD.toast(project.name + ' selected');
      return;
    }
    if (action === 'manage-projects') {
      closeSheet();
      navigate('#/projects');
      return;
    }
    if (action === 'dismiss-demo') {
      AD.state.data.settings.demoNoticeDismissed = true;
      AD.save();
      closeSheet();
      AD.render();
      AD.toast('Demo notice dismissed');
      return;
    }
    if (action === 'approve-confirm') {
      if (!sheet.checked) {
        sheet.error = 'Confirm that you reviewed the changes.';
        renderSheet();
        return;
      }
      const result = AD.approveTask(sheet.taskId);
      if (!result.ok) {
        sheet.error = result.error;
        renderSheet();
        return;
      }
      closeSheet();
      AD.render();
      AD.toast('Approved · recorded in task history');
      AD.announce('Task approved');
      const heading = document.querySelector('.task-heading h1');
      if (heading) heading.focus({ preventScroll: true });
      return;
    }
    if (action === 'request-submit') {
      const result = AD.requestChanges(sheet.taskId, sheet.feedback);
      if (!result.ok) {
        sheet.error = result.error;
        renderSheet();
        return;
      }
      closeSheet();
      AD.state.ui.forceBottom = true;
      navigate('#/task/' + encodeURIComponent(sheet.taskId) + '/chat');
      AD.toast('Feedback sent · task reopened');
      AD.focusComposer();
      return;
    }
    if (action === 'select-model') {
      const draft = AD.getNewTaskDraft();
      const model = AD.state.data.agents.find(function (item) { return item.id === button.getAttribute('data-model'); });
      if (!model) return;
      if (model.provider === 'local' && model.state !== 'ready') return;
      draft.agentId = model.id;
      draft.error = '';
      draft.errorKind = '';
      closeSheet();
      AD.render();
      return;
    }
    if (action === 'manage-models') {
      closeSheet();
      navigate('#/settings');
      AD.toast('Manage local models in Settings');
      return;
    }
    if (action === 'confirm') {
      const actionName = sheet.confirmAction;
      closeSheet();
      if (actionName === 'disconnect') {
        AD.disconnectHost();
      } else if (actionName === 'reset') {
        AD.resetData();
        navigate('#/home');
        AD.render();
        AD.toast('Demo data reset');
        AD.announce('Demo data reset');
      } else if (actionName === 'signout') {
        navigate('#/signed-out');
      }
    }
  }

  /* ---------- click handling ---------- */

  function syncSearchClear(input) {
    const field = input.closest('.search-field');
    if (!field) return;
    let button = field.querySelector('.search-clear');
    if (input.value && !button) {
      button = document.createElement('button');
      button.className = 'search-clear';
      button.type = 'button';
      button.setAttribute('aria-label', 'Clear search');
      button.setAttribute('data-action', 'clear-search');
      button.setAttribute('data-target', input.id);
      button.innerHTML = icon('x');
      field.appendChild(button);
    } else if (!input.value && button) {
      button.remove();
    }
  }

  document.addEventListener('click', function (event) {
    const trigger = event.target.closest('[data-action]');
    if (!trigger) return;
    const action = trigger.getAttribute('data-action');
    const task = currentTask();

    if (action === 'back') { goBack(); return; }
    if (action === 'new-task') { navigate('#/new'); return; }
    if (action === 'view-all') {
      AD.state.ui.statusFilter = 'all';
      AD.state.ui.taskSearch = '';
      navigate('#/tasks');
      return;
    }
    if (action === 'go-settings') { navigate('#/settings'); return; }
    if (action === 'model-sheet') { openSheet({ type: 'models' }); return; }
    if (action === 'load-model') { AD.loadLocalModel(trigger.getAttribute('data-model')); return; }
    if (action === 'unload-model') { AD.unloadLocalModel(trigger.getAttribute('data-model')); return; }
    if (action === 'import-model') { AD.importLocalModel(trigger.getAttribute('data-model')); return; }
    if (action === 'project-sheet') { openProjectSheet(); return; }
    if (action === 'open-project') {
      const project = AD.getProject(trigger.getAttribute('data-project'));
      if (!project) return;
      AD.state.data.settings.activeProjectId = project.id;
      AD.state.data.settings.defaultProjectId = project.id;
      AD.state.ui.statusFilter = 'all';
      AD.state.ui.taskSearch = '';
      AD.state.ui.homeSearch = '';
      AD.save();
      navigate('#/tasks');
      AD.toast(project.name + ' selected');
      return;
    }
    if (action === 'open-filter') {
      AD.state.ui.statusFilter = trigger.getAttribute('data-filter') || 'all';
      navigate('#/tasks');
      return;
    }
    if (action === 'set-filter') {
      AD.state.ui.statusFilter = trigger.getAttribute('data-filter') || 'all';
      AD.render();
      return;
    }
    if (action === 'clear-filters') {
      AD.state.ui.statusFilter = 'all';
      AD.state.ui.taskSearch = '';
      AD.render();
      return;
    }
    if (action === 'clear-search') {
      const target = trigger.getAttribute('data-target');
      if (target === 'home-search') AD.state.ui.homeSearch = '';
      if (target === 'task-search') AD.state.ui.taskSearch = '';
      AD.render();
      return;
    }
    if (action === 'dismiss-demo') {
      AD.state.data.settings.demoNoticeDismissed = true;
      AD.save();
      AD.render();
      return;
    }
    if (action === 'demo-info') { openSheet({ type: 'demo' }); return; }
    if (action === 'reconnect-host') { AD.reconnectHost(); return; }
    if (action === 'disconnect-host') {
      openConfirm({
        title: 'Disconnect Studio Mac?',
        body: 'Running tasks will pause and messages cannot be sent until you reconnect. This is a simulated host in the demo.',
        confirmLabel: 'Disconnect',
        confirmAction: 'disconnect',
        danger: true
      });
      return;
    }
    if (action === 'reset-demo') {
      openConfirm({
        title: 'Reset demo data?',
        body: 'This deletes every local task, message, change, check result, and decision, then restores the sample workspace. It cannot be undone.',
        confirmLabel: 'Reset demo data',
        confirmAction: 'reset',
        danger: true
      });
      return;
    }
    if (action === 'sign-out') {
      openConfirm({
        title: 'Sign out?',
        body: 'You will return to the demo sign-in screen. Local demo data stays on this device.',
        confirmLabel: 'Sign out',
        confirmAction: 'signout'
      });
      return;
    }
    if (action === 'sign-in') {
      navigate('#/home');
      AD.toast('Signed in to the demo workspace');
      return;
    }
    if (!task) return;
    if (action === 'toggle-activity') {
      AD.state.ui.activityOpen[task.id] = !AD.state.ui.activityOpen[task.id];
      AD.render();
      return;
    }
    if (action === 'allow-request') { AD.allowPermission(task.id); return; }
    if (action === 'decline-request') { AD.declinePermission(task.id); return; }
    if (action === 'retry-task') { AD.retryTask(task.id); return; }
    if (action === 'retry-message') {
      AD.retryMessage(task.id, trigger.getAttribute('data-message'));
      return;
    }
    if (action === 'approve') { openApproveSheet(task.id); return; }
    if (action === 'request-changes') { openRequestSheet(task.id); return; }
    if (action === 'goto-changes') {
      AD.state.ui.diffFileId = null;
      AD.state.ui.forceTop = true;
      navigate('#/task/' + encodeURIComponent(task.id) + '/changes');
      return;
    }
    if (action === 'goto-checks') {
      AD.state.ui.forceTop = true;
      navigate('#/task/' + encodeURIComponent(task.id) + '/checks');
      return;
    }
    if (action === 'open-diff') {
      AD.state.ui.diffFileId = trigger.getAttribute('data-file');
      AD.state.ui.forceTop = true;
      AD.render();
      return;
    }
    if (action === 'close-diff') {
      AD.state.ui.diffFileId = null;
      AD.state.ui.forceTop = true;
      AD.render();
      return;
    }
    if (action === 'copy-path') {
      AD.copyText(trigger.getAttribute('data-path')).then(function (ok) {
        AD.toast(ok ? 'File path copied' : 'Could not copy the path');
      });
      return;
    }
    if (action === 'ask-check') {
      const check = task.checks.find(function (item) { return item.id === trigger.getAttribute('data-check'); });
      if (!check) return;
      const excerpt = (check.output || check.summary || '').split('\n').slice(0, 5).join('\n');
      AD.state.ui.drafts[task.id] = check.name + ' failed. Can you look into this?\n\n' + excerpt;
      AD.state.ui.forceBottom = true;
      navigate('#/task/' + encodeURIComponent(task.id) + '/chat');
      AD.focusComposer();
      AD.toast('Draft added to the message box');
    }
  });

  document.addEventListener('submit', function (event) {
    if (event.target.id === 'composer-form') {
      event.preventDefault();
      const task = currentTask();
      const input = document.getElementById('composer-input');
      if (!task || !input || AD.state.ui.sending) return;
      if (AD.state.ui.localTyping[task.id]) return;
      if (AD.isLocalTask(task) && !AD.modelReady(task.agentId)) {
        AD.toast('Load the local model in Settings before sending');
        return;
      }
      if (!input.value.trim()) return;
      AD.sendMessage(task.id, input.value);
      return;
    }
    if (event.target.id === 'new-task-form') {
      event.preventDefault();
      submitNewTask();
    }
  });

  document.addEventListener('input', function (event) {
    const target = event.target;
    if (target.id === 'home-search') {
      AD.state.ui.homeSearch = target.value;
      AD.updateHomeResults();
      syncSearchClear(target);
      return;
    }
    if (target.id === 'task-search') {
      AD.state.ui.taskSearch = target.value;
      AD.updateTaskResults();
      syncSearchClear(target);
      return;
    }
    if (target.id === 'composer-input') {
      const task = currentTask();
      if (task) AD.state.ui.drafts[task.id] = target.value;
      AD.autoGrow(target);
      const button = target.closest('form').querySelector('.send-btn');
      const typing = Boolean(task && AD.state.ui.localTyping[task.id]);
      if (button) button.disabled = !target.value.trim() || typing;
      return;
    }
    const draft = AD.state.ui.newTask;
    if (draft && target.id === 'nt-title') {
      draft.title = target.value;
      if (!draft.branchTouched) {
        draft.branch = 'feature/' + (AD.slugify(target.value) || 'new-task');
        const branchInput = document.getElementById('nt-branch');
        if (branchInput) branchInput.value = draft.branch;
      }
      return;
    }
    if (draft && target.id === 'nt-prompt') {
      draft.prompt = target.value;
      return;
    }
    if (draft && target.id === 'nt-branch') {
      draft.branch = target.value;
      draft.branchTouched = true;
      return;
    }
    const sheet = AD.state.ui.sheet;
    if (sheet && sheet.type === 'request' && target.id === 'request-feedback') {
      sheet.feedback = target.value;
      const button = sheetElement().querySelector('[data-sheet-action="request-submit"]');
      if (button) button.disabled = !target.value.trim() || !AD.canReachHost();
    }
  });

  document.addEventListener('change', function (event) {
    const target = event.target;
    if (target.id === 'nt-project') {
      const draft = AD.getNewTaskDraft();
      draft.projectId = target.value;
      if (!draft.branchTouched) {
        draft.branch = 'feature/' + (AD.slugify(draft.title) || 'new-task');
      }
      AD.render();
      return;
    }
    if (target.id === 'default-project') {
      const project = AD.getProject(target.value);
      if (!project) return;
      AD.state.data.settings.defaultProjectId = project.id;
      AD.state.data.settings.activeProjectId = project.id;
      AD.save();
      AD.render();
      AD.toast(project.name + ' is now the active project');
      return;
    }
    if (target.id === 'default-agent') {
      const model = AD.state.data.agents.find(function (item) { return item.id === target.value; });
      if (!model) return;
      if (model.provider === 'local' && model.state !== 'ready') {
        AD.toast('Load this local model before using it as the default');
        return;
      }
      AD.state.data.settings.defaultAgentId = target.value;
      AD.save();
      AD.toast(model.provider === 'local' ? 'Default model · runs on this phone' : 'Default model updated');
      return;
    }
    if (target.id === 'diff-file-select') {
      AD.state.ui.diffFileId = target.value;
      AD.state.ui.forceTop = true;
      AD.render();
      return;
    }
    if (target.id === 'approve-reviewed') {
      const sheet = AD.state.ui.sheet;
      if (!sheet || sheet.type !== 'approve') return;
      sheet.checked = target.checked;
      sheet.error = '';
      const button = sheetElement().querySelector('[data-sheet-action="approve-confirm"]');
      if (button) button.disabled = !target.checked || !AD.canReachHost();
    }
  });

  document.addEventListener('keydown', function (event) {
    if (event.target.id === 'nt-title' && event.key === 'Enter') {
      event.preventDefault();
      const prompt = document.getElementById('nt-prompt');
      if (prompt) prompt.focus();
      return;
    }
    if (event.target.id === 'composer-input' && event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      const form = document.getElementById('composer-form');
      if (form) form.requestSubmit();
    }
  });

  function showNewTaskError(draft, message, kind) {
    draft.error = message;
    draft.errorKind = kind;
    AD.render();
    AD.toast(message);
    window.setTimeout(function () {
      const error = document.querySelector('.form-error');
      if (error) {
        const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        error.scrollIntoView({ block: 'center', behavior: reduce ? 'auto' : 'smooth' });
      }
    }, 80);
  }

  function submitNewTask() {
    if (AD.state.ui.creating) return;
    const draft = AD.getNewTaskDraft();
    const title = draft.title.trim();
    const prompt = draft.prompt.trim();
    const local = AD.isLocalModel(draft.agentId);
    if (!title && !prompt) {
      showNewTaskError(draft, 'Add a title or instructions before creating the task.', 'validation');
      return;
    }
    if (local && !AD.modelReady(draft.agentId)) {
      showNewTaskError(draft, 'This local model is not loaded. Load it in Settings, or choose another model.', 'model');
      return;
    }
    if (!local && !AD.canReachHost()) {
      showNewTaskError(draft, 'Studio Mac is offline. Reconnect, then try again. Your draft is saved.', 'offline');
      return;
    }
    const project = AD.getProject(draft.projectId) || AD.activeProject();
    const finalTitle = title || prompt.split(/\r?\n/)[0].slice(0, 64);
    const finalPrompt = prompt || title;
    const branch = ((draft.branch.trim() || ('feature/' + (AD.slugify(finalTitle) || 'new-task')))).replace(/\s+/g, '-');
    draft.error = '';
    draft.errorKind = '';
    AD.state.ui.creating = true;
    AD.render();
    window.setTimeout(function () {
      if (local ? !AD.modelReady(draft.agentId) : !AD.canReachHost()) {
        AD.state.ui.creating = false;
        if (local) {
          showNewTaskError(draft, 'The local model is no longer ready. Load it in Settings and try again.', 'model');
        } else {
          showNewTaskError(draft, 'Studio Mac went offline. Your draft is saved. Reconnect and try again.', 'offline');
        }
        return;
      }
      AD.state.data.settings.activeProjectId = project.id;
      const task = AD.createTaskRecord({
        title: finalTitle,
        prompt: finalPrompt,
        projectId: project.id,
        branch: branch,
        agentId: draft.agentId
      });
      AD.state.ui.creating = false;
      AD.state.ui.newTask = null;
      AD.state.ui.forceBottom = true;
      if (AD.state.route.name === 'new') {
        navigate('#/task/' + encodeURIComponent(task.id) + '/chat');
      } else {
        AD.render();
      }
      AD.toast(local ? 'Local task created · chat on this phone' : 'Task created · queued');
      AD.announce(local ? 'Local task created. Chat is available on this phone.' : 'Task created and queued');
    }, 550);
  }

  /* ---------- boot ---------- */

  function updateTitle() {
    const route = AD.state.route;
    if (!route) return;
    if (route.name === 'task') {
      const task = AD.getTask(route.params.id);
      document.title = task ? task.title + ' · AgentDeck' : 'AgentDeck';
    } else if (route.name === 'home') {
      document.title = 'AgentDeck';
    } else {
      const label = route.name === 'signed-out' ? 'Signed out' : route.name.charAt(0).toUpperCase() + route.name.slice(1);
      document.title = label + ' · AgentDeck';
    }
  }

  function syncAppHeight() {
    const viewport = window.visualViewport;
    const height = viewport ? viewport.height : window.innerHeight;
    document.documentElement.style.setProperty('--app-height', Math.round(height) + 'px');
  }

  function bindGlobalEvents() {
    window.addEventListener('resize', syncAppHeight);
    if (window.visualViewport) {
      window.visualViewport.addEventListener('resize', syncAppHeight);
      window.visualViewport.addEventListener('scroll', syncAppHeight);
    }
    window.addEventListener('hashchange', function () {
      const previous = AD.state.route;
      AD.state.route = AD.parseRoute();
      if (backNavigation) {
        AD.state.depth = Math.max(0, AD.state.depth - 1);
        backNavigation = false;
      } else {
        AD.state.depth += 1;
      }
      const sameTask = previous && previous.name === 'task' &&
        AD.state.route.name === 'task' && previous.params.id === AD.state.route.params.id;
      if (!sameTask || AD.state.route.params.tab !== 'changes') {
        AD.state.ui.diffFileId = null;
      }
      closeSheet();
      AD.render();
      updateTitle();
    });

    const dialog = sheetElement();
    if (dialog) {
      dialog.addEventListener('click', function (event) {
        if (event.target === dialog) {
          closeSheet();
          return;
        }
        const button = event.target.closest('[data-sheet-action]');
        if (!button) return;
        handleSheetAction(button.getAttribute('data-sheet-action'), button);
      });
      dialog.addEventListener('close', function () {
        AD.state.ui.sheet = null;
        document.body.classList.remove('sheet-open');
      });
    }

    window.setInterval(AD.syncTimeLabels, 30000);
  }

  document.addEventListener('DOMContentLoaded', function () {
    AD.state.data = AD.load();
    let recoveredMessage = false;
    AD.state.data.tasks.forEach(function (task) {
      task.messages.forEach(function (message) {
        if (message.state === 'sending') {
          message.state = 'failed';
          recoveredMessage = true;
        }
      });
    });
    if (!location.hash) {
      try { history.replaceState(null, '', '#/home'); } catch (error) { /* file:// fallback */ }
    }
    AD.state.route = AD.parseRoute();
    AD.state.depth = 0;
    AD.state.lastKey = null;
    bindGlobalEvents();
    syncAppHeight();
    AD.state.ui.booting = true;
    AD.render();
    updateTitle();
    window.setTimeout(function () {
      AD.state.ui.booting = false;
      AD.state.lastKey = null;
      AD.render();
      updateTitle();
      AD.resumeSimulations();
      if (recoveredMessage) {
        AD.save();
        AD.announce('A message was not confirmed and can be retried.');
      }
    }, 520);
  });
})(window.AD);
