(function (AD) {
  'use strict';

  const esc = AD.esc;
  const icon = AD.icon;

  AD.statusPill = function (status) {
    const meta = AD.STATUS[status] || AD.STATUS.pending;
    return '<span class="status-pill tone-' + meta.tone + ' s-' + status + '">' +
      icon(meta.icon, status === 'in_progress' ? 'spin' : '') +
      '<span>' + meta.label + '</span></span>';
  };

  AD.tabbar = function (active) {
    const tabs = [
      { id: 'home', label: 'Home', icon: 'home' },
      { id: 'tasks', label: 'Tasks', icon: 'tasks' },
      { id: 'projects', label: 'Projects', icon: 'grid' },
      { id: 'settings', label: 'Settings', icon: 'settings' }
    ];
    return '<nav class="tabbar" aria-label="Primary">' + tabs.map(function (tab) {
      const isActive = tab.id === active;
      return '<a class="tab' + (isActive ? ' active' : '') + '" href="#/' + tab.id + '"' +
        (isActive ? ' aria-current="page"' : '') + '>' +
        '<span class="tab-icon">' + icon(tab.icon) + '</span>' +
        '<span class="tab-label">' + tab.label + '</span></a>';
    }).join('') + '</nav>';
  };

  AD.searchField = function (options) {
    return '<div class="search-field">' + icon('search') +
      '<input id="' + options.id + '" type="search" value="' + esc(options.value) + '"' +
      ' placeholder="' + esc(options.placeholder) + '" autocomplete="off"' +
      ' aria-label="' + esc(options.label || options.placeholder) + '" enterkeyhint="search">' +
      (options.value ? '<button class="search-clear" type="button" data-action="clear-search" data-target="' +
        options.id + '" aria-label="Clear search">' + icon('x') + '</button>' : '') +
      '</div>';
  };

  AD.rootHeader = function (title, subtitle, actions) {
    return '<header class="topbar">' +
      '<div class="topbar-title"><h1>' + esc(title) + '</h1>' +
      (subtitle ? '<div class="topbar-sub">' + subtitle + '</div>' : '') + '</div>' +
      '<div class="topbar-actions">' + (actions || '') + '</div></header>';
  };

  AD.emptyState = function (options) {
    return '<div class="empty-state">' +
      '<span class="empty-icon">' + icon(options.icon || 'search') + '</span>' +
      '<h3>' + esc(options.title) + '</h3>' +
      '<p>' + esc(options.text) + '</p>' +
      (options.action || '') + '</div>';
  };

  AD.hostBanner = function () {
    if (AD.canReachHost()) return '';
    return '<div class="banner warn">' + icon('cloud-off') +
      '<div class="banner-copy"><strong>Studio Mac is offline</strong>' +
      '<span>Local model chat still works. Host models and execution need a reconnect.</span></div>' +
      '<button class="banner-action" data-action="reconnect-host"' + (AD.state.ui.connecting ? ' disabled' : '') + '>' +
      (AD.state.ui.connecting ? '<span class="spinner tiny"></span>Reconnecting…' : 'Reconnect') + '</button></div>';
  };

  AD.demoNotice = function () {
    if (AD.state.data.settings.demoNoticeDismissed) return '';
    return '<div class="notice demo-notice">' +
      '<span class="notice-icon">' + icon('info') + '</span>' +
      '<div class="notice-copy"><strong>Demo workspace</strong>' +
      '<span>Tasks, agent messages, diffs, and checks are simulated on this device.</span>' +
      '<button class="inline-link" data-action="demo-info">How this works</button></div>' +
      '<button class="icon-btn tiny" data-action="dismiss-demo" aria-label="Dismiss demo notice">' + icon('x') + '</button>' +
      '</div>';
  };

  AD.projectSwitcher = function () {
    const project = AD.activeProject();
    const hostOnline = AD.canReachHost();
    const host = AD.state.data.host;
    return '<button class="project-card" data-action="project-sheet" aria-haspopup="dialog">' +
      '<span class="project-mark">' + icon('folder') + '</span>' +
      '<span class="project-main"><span class="project-name">' + esc(project.name) + '</span>' +
      '<span class="project-meta">' + esc(project.repo) + ' · ' + esc(project.defaultBranch) + '</span></span>' +
      '<span class="sync-state ' + (hostOnline ? 'online' : 'offline') + '">' +
      '<span class="dot"></span>' + (hostOnline ? 'Synced <span data-ts="' + esc(host.lastSeen) + '">' + AD.timeAgo(host.lastSeen) + '</span>' : 'Offline') + '</span>' +
      icon('chevron-down') + '</button>';
  };

  AD.statGrid = function (tasks) {
    const counts = AD.statusCounts(tasks);
    const stats = [
      { filter: 'active', label: 'Active', count: counts.in_progress, icon: 'activity', tone: 'blue' },
      { filter: 'needs_input', label: 'Needs input', count: counts.needs_input, icon: 'message', tone: 'violet' },
      { filter: 'review', label: 'Review', count: counts.review, icon: 'eye', tone: 'amber' },
      { filter: 'pending', label: 'Pending', count: counts.pending, icon: 'clock', tone: 'gray' },
      { filter: 'failed', label: 'Failed', count: counts.failed, icon: 'alert', tone: 'red' },
      { filter: 'completed', label: 'Completed', count: counts.completed, icon: 'check-circle', tone: 'green' }
    ];
    return '<div class="stats-grid">' + stats.map(function (stat) {
      return '<button class="stat-tile" data-action="open-filter" data-filter="' + stat.filter + '"' +
        ' aria-label="' + stat.count + ' ' + stat.label + ' tasks. Show these tasks.">' +
        '<span class="stat-top"><span class="stat-icon tone-' + stat.tone + '">' + icon(stat.icon) + '</span>' +
        '<span class="stat-count">' + stat.count + '</span></span>' +
        '<span class="stat-label">' + stat.label + '</span></button>';
    }).join('') + '</div>';
  };

  AD.taskCard = function (task) {
    const project = AD.getProject(task.projectId);
    const summary = AD.checkSummary(task.checks);
    const fileCount = task.changedFiles.length;
    const local = AD.isLocalModel(task.agentId);
    let foot = '';
    if (fileCount || task.checks.length) {
      foot = '<div class="task-foot">' +
        (fileCount ? '<span>' + icon('file') + AD.plural(fileCount, 'file') + '</span>' : '') +
        (task.checks.length ? '<span class="checks tone-' + summary.tone + '">' + icon(AD.checkSummary(task.checks).counts.failed ? 'x-circle' : 'check-circle') + esc(summary.label) + '</span>' : '') +
        '</div>';
    }
    return '<a class="task-card" href="#/task/' + encodeURIComponent(task.id) + '/chat">' +
      '<div class="task-card-head"><span class="task-title">' + esc(task.title) + '</span>' + AD.statusPill(task.status) + '</div>' +
      '<div class="task-meta"><span>' + esc(project ? project.name : '') + '</span><span class="dot-sep">·</span>' +
      '<span>' + esc(AD.agentName(task.agentId)) + '</span>' +
      (local ? '<span class="local-chip">' + icon('phone') + 'Local</span>' : '') +
      '<span class="dot-sep">·</span>' +
      '<span data-ts="' + esc(task.updatedAt) + '">' + AD.timeAgo(task.updatedAt) + '</span></div>' +
      foot + '</a>';
  };

  AD.taskList = function (tasks, emptyOptions) {
    if (!tasks.length) {
      return AD.emptyState(emptyOptions || {
        icon: 'tasks',
        title: 'No tasks here',
        text: 'Nothing matches this view yet.'
      });
    }
    return '<div class="task-list">' + tasks.map(AD.taskCard).join('') + '</div>';
  };

  AD.screenHome = function () {
    const project = AD.activeProject();
    const projectTasks = AD.tasksForProject(project.id);
    const filtered = AD.filterTasks(projectTasks, AD.state.ui.homeSearch, 'all');
    const recent = (AD.state.ui.homeSearch ? filtered : filtered.slice(0, 4));
    const counts = AD.statusCounts(projectTasks);
    const openCount = projectTasks.length - counts.completed;
    return '<div class="screen">' +
      '<header class="topbar home-topbar">' +
      '<div class="wordmark-block"><h1 class="wordmark">AgentDeck</h1><span class="wordmark-sub">Coding workspace</span></div>' +
      '<div class="topbar-actions"><button class="demo-chip" data-action="demo-info" aria-label="About this demo">' +
      icon('info') + 'Demo</button>' +
      '<button class="avatar" data-action="go-settings" aria-label="Open settings">R</button></div></header>' +
      '<main id="main" class="screen-body home-body" data-body="1">' +
      AD.hostBanner() +
      AD.demoNotice() +
      AD.projectSwitcher() +
      '<div class="home-actions">' +
      AD.searchField({ id: 'home-search', value: AD.state.ui.homeSearch, placeholder: 'Search tasks', label: 'Search tasks in ' + project.name }) +
      '<button class="btn btn-dark new-task-btn" data-action="new-task">' + icon('plus') + 'New task</button>' +
      '</div>' +
      '<section class="home-section" aria-labelledby="status-heading">' +
      '<div class="section-head"><h2 id="status-heading">Status</h2><span class="section-hint">' + AD.plural(openCount, 'open task') + '</span></div>' +
      AD.statGrid(projectTasks) + '</section>' +
      '<section class="home-section" aria-labelledby="recent-heading">' +
      '<div class="section-head"><h2 id="recent-heading">' + (AD.state.ui.homeSearch ? 'Results' : 'Recent tasks') + '</h2>' +
      '<a class="section-link" href="#/tasks" data-action="view-all">View all' + icon('chevron-right') + '</a></div>' +
      '<div id="home-results">' + AD.taskList(recent, {
        icon: 'search',
        title: AD.state.ui.homeSearch ? 'No matching tasks' : 'No tasks yet',
        text: AD.state.ui.homeSearch ? 'Try a different title, project, or branch.' : 'Create a task to get started.',
        action: AD.state.ui.homeSearch ? '<button class="btn btn-outline small" data-action="clear-search" data-target="home-search">Clear search</button>' : '<a class="btn btn-dark small" href="#/new">New task</a>'
      }) + '</div></section>' +
      '<p class="quiet-footer">Demo data · saved on this device · not connected to a real host</p>' +
      '</main>' + AD.tabbar('home') + '</div>';
  };

  AD.filterChips = function (tasks) {
    const counts = AD.statusCounts(tasks);
    const filter = AD.state.ui.statusFilter;
    const entries = [
      { id: 'all', label: 'All', count: tasks.length },
      { id: 'active', label: 'Active', count: counts.in_progress },
      { id: 'needs_input', label: 'Needs input', count: counts.needs_input },
      { id: 'review', label: 'Review', count: counts.review },
      { id: 'pending', label: 'Pending', count: counts.pending },
      { id: 'failed', label: 'Failed', count: counts.failed },
      { id: 'completed', label: 'Completed', count: counts.completed }
    ];
    return '<div class="chip-row" role="group" aria-label="Filter by status">' + entries.map(function (entry) {
      return '<button class="chip' + (filter === entry.id ? ' active' : '') + '" data-action="set-filter"' +
        ' data-filter="' + entry.id + '" aria-pressed="' + (filter === entry.id) + '">' +
        esc(entry.label) + '<span class="chip-count">' + entry.count + '</span></button>';
    }).join('') + '</div>';
  };

  AD.screenTasks = function () {
    const project = AD.activeProject();
    const tasks = AD.tasksForProject(project.id);
    const filtered = AD.filterTasks(tasks, AD.state.ui.taskSearch, AD.state.ui.statusFilter);
    return '<div class="screen">' +
      AD.rootHeader('Tasks', '<button class="header-project" data-action="project-sheet">' + icon('folder') +
        esc(project.name) + icon('chevron-down') + '</button>',
        '<a class="icon-btn filled" href="#/new" aria-label="New task">' + icon('plus') + '</a>') +
      '<main id="main" class="screen-body" data-body="1">' +
      AD.hostBanner() +
      '<div class="tasks-controls">' +
      AD.searchField({ id: 'task-search', value: AD.state.ui.taskSearch, placeholder: 'Search tasks', label: 'Search tasks' }) +
      AD.filterChips(tasks) +
      '</div>' +
      '<div class="list-meta">' +
      '<span id="task-count-label">' + AD.plural(filtered.length, 'task') + '</span>' +
      '<button class="inline-link" data-action="project-sheet">Switch project</button></div>' +
      '<div id="task-results">' + AD.taskList(filtered, {
        icon: 'search',
        title: 'No tasks match',
        text: 'Adjust the search or status filter.',
        action: '<button class="btn btn-outline small" data-action="clear-filters">Clear filters</button>'
      }) + '</div>' +
      '<div class="bottom-space"></div></main>' + AD.tabbar('tasks') + '</div>';
  };

  AD.screenProjects = function () {
    const host = AD.state.data.host;
    const online = AD.canReachHost();
    const projectsHtml = AD.state.data.projects.map(function (project) {
      const tasks = AD.tasksForProject(project.id);
      const counts = AD.statusCounts(tasks);
      const open = tasks.length - counts.completed;
      const isActive = project.id === AD.state.data.settings.activeProjectId;
      return '<button class="project-row' + (isActive ? ' active' : '') + '" data-action="open-project" data-project="' + esc(project.id) + '">' +
        '<span class="project-mark large">' + icon('folder') + '</span>' +
        '<span class="project-row-main"><span class="project-row-title">' + esc(project.name) +
        (isActive ? '<span class="active-tag">' + icon('check') + 'Active</span>' : '') + '</span>' +
        '<span class="project-row-meta">' + esc(project.repo) + ' · ' + esc(project.defaultBranch) + '</span>' +
        '<span class="project-row-stats">' + AD.plural(open, 'open task') + ' · ' + counts.review + ' in review · ' + counts.completed + ' completed</span>' +
        '</span>' + icon('chevron-right') + '</button>';
    }).join('');
    return '<div class="screen">' +
      AD.rootHeader('Projects', '<p class="header-sub">Switch the workspace the app is scoped to.</p>',
        '<a class="icon-btn filled" href="#/new" aria-label="New task">' + icon('plus') + '</a>') +
      '<main id="main" class="screen-body" data-body="1">' +
      AD.hostBanner() +
      '<section class="card host-card">' +
      '<div class="row-between"><div class="host-identity"><span class="host-mark">' + icon('server') + '</span>' +
      '<div><strong>' + esc(host.name) + '</strong><p>' + esc(host.platform) + ' · ' + esc(host.transport) + '</p></div></div>' +
      '<span class="connection-pill ' + (online ? 'online' : 'offline') + '"><span class="dot"></span>' + (online ? 'Connected' : 'Offline') + '</span></div>' +
      '<p class="card-note">' + (online ? 'Projects were synced ' + AD.timeAgo(host.lastSeen) + '.' : 'Reconnect the host to resume tasks and sync projects.') + '</p>' +
      '<div class="card-actions">' +
      (online ? '<button class="btn btn-outline small" data-action="go-settings">Manage connection</button>' :
        '<button class="btn btn-dark small" data-action="reconnect-host"' + (AD.state.ui.connecting ? ' disabled' : '') + '>' +
        (AD.state.ui.connecting ? '<span class="spinner"></span>Reconnecting…' : 'Reconnect host') + '</button>') +
      '</div></section>' +
      '<h2 class="list-heading">Your projects</h2>' +
      '<div class="project-list">' + projectsHtml + '</div>' +
      '<div class="bottom-space"></div></main>' + AD.tabbar('projects') + '</div>';
  };

  AD.screenSettings = function () {
    const data = AD.state.data;
    const host = data.host;
    const online = AD.canReachHost();
    const projectOptions = data.projects.map(function (project) {
      return '<option value="' + esc(project.id) + '"' + (project.id === data.settings.defaultProjectId ? ' selected' : '') + '>' + esc(project.name) + '</option>';
    }).join('');
    const modelOption = function (model) {
      const state = AD.modelState(model);
      const local = model.provider === 'local';
      const label = model.name + (local ? ' · ' + (model.state === 'ready' ? 'this phone' : state.label) : ' · ' + (model.providerLabel || 'host'));
      const disabled = local && model.state !== 'ready';
      return '<option value="' + esc(model.id) + '"' + (model.id === data.settings.defaultAgentId ? ' selected' : '') +
        (disabled ? ' disabled' : '') + '>' + esc(label) + '</option>';
    };
    const localModels = AD.localModels();
    const defaultModelOptions = '<optgroup label="On this device">' +
      data.agents.filter(function (model) { return model.provider === 'local'; }).map(modelOption).join('') +
      '</optgroup><optgroup label="Host models">' +
      data.agents.filter(function (model) { return model.provider !== 'local'; }).map(modelOption).join('') +
      '</optgroup>';
    const localRows = localModels.map(function (model) {
      const state = AD.modelState(model);
      let action;
      if (model.state === 'ready') {
        action = '<button class="btn btn-outline small" data-action="unload-model" data-model="' + esc(model.id) + '">Unload</button>';
      } else if (model.state === 'unloaded') {
        action = '<button class="btn btn-dark small" data-action="load-model" data-model="' + esc(model.id) + '">Load</button>';
      } else if (model.state === 'not_imported') {
        action = '<button class="btn btn-dark small" data-action="import-model" data-model="' + esc(model.id) + '">Import</button>';
      } else {
        action = '<button class="btn btn-outline small" disabled><span class="spinner dark"></span>' + esc(state.label) + '</button>';
      }
      return '<div class="local-model-row">' +
        '<span class="local-model-icon">' + icon('cpu') + '</span>' +
        '<div class="local-model-main"><div class="local-model-title"><strong>' + esc(model.name) + '</strong>' +
        '<span class="model-state tone-' + state.tone + '">' + esc(state.label) + '</span></div>' +
        '<span>' + esc(model.quantization) + ' · ' + model.sizeGB + ' GB · ' + model.contextK + 'K context · ' + model.minRamGB + ' GB RAM min</span></div>' +
        '<div class="local-model-actions">' + action + '</div></div>';
    }).join('');
    const importedGB = localModels.reduce(function (sum, model) {
      return sum + (model.state === 'not_imported' ? 0 : model.sizeGB);
    }, 0);
    return '<div class="screen">' +
      AD.rootHeader('Settings', '<p class="header-sub">Host, on-device models, and demo data.</p>', '') +
      '<main id="main" class="screen-body" data-body="1">' +
      '<h2 class="list-heading">Execution host</h2>' +
      '<section class="card host-card">' +
      '<div class="row-between"><div class="host-identity"><span class="host-mark">' + icon('server') + '</span>' +
      '<div><strong>' + esc(host.name) + '</strong><p>' + esc(host.platform) + ' · ' + esc(host.transport) + '</p></div></div>' +
      '<span class="connection-pill ' + (online ? 'online' : 'offline') + '"><span class="dot"></span>' + (online ? 'Connected' : 'Offline') + '</span></div>' +
      '<p class="card-note">' + (online ? 'Last seen ' + AD.timeAgo(host.lastSeen) + '. Host models and all task execution use this simulated host.' : 'Task execution is paused. On-device chat remains available.') + '</p>' +
      '<div class="card-actions">' +
      (online ? '<button class="btn btn-outline small danger-text" data-action="disconnect-host">Disconnect</button>' :
        '<button class="btn btn-dark small" data-action="reconnect-host"' + (AD.state.ui.connecting ? ' disabled' : '') + '>' +
        (AD.state.ui.connecting ? '<span class="spinner"></span>Connecting…' : 'Reconnect host') + '</button>') +
      '<span class="fine">Host state is simulated in this demo.</span></div></section>' +
      '<h2 class="list-heading">On-device models</h2>' +
      '<section class="card local-card">' +
      '<div class="local-card-head"><div class="host-identity"><span class="host-mark local">' + icon('phone') + '</span>' +
      '<div><strong>Local inference</strong><p>Runs on this phone · web preview</p></div></div>' +
      '<span class="preview-pill">Simulated</span></div>' +
      '<div class="local-model-list">' + localRows + '</div>' +
      '<div class="local-storage">' + icon('hard-drive') + '<span>' + importedGB.toFixed(1) + ' GB on device</span>' +
      '<span class="dot-sep">·</span><span>' + localModels.length + ' models known</span></div>' +
      '<p class="card-note">The web preview does not run the model. The native build will load these GGUF models on-device. File changes and checks still use the execution host.</p>' +
      '</section>' +
      '<h2 class="list-heading">Defaults for new tasks</h2>' +
      '<section class="card settings-list">' +
      '<label class="setting-row"><span class="setting-label">Project</span><span class="setting-value"><select class="select" id="default-project">' + projectOptions + '</select></span></label>' +
      '<label class="setting-row"><span class="setting-label">Default model</span><span class="setting-value"><select class="select" id="default-agent">' + defaultModelOptions + '</select></span></label>' +
      '</section>' +
      '<h2 class="list-heading">Standard permissions</h2>' +
      '<section class="card permissions-card">' +
      '<div class="permission-row"><span class="permission-icon allowed">' + icon('check') + '</span><div><strong>Read and edit project files</strong><span>Limited to the selected repository.</span></div></div>' +
      '<div class="permission-row"><span class="permission-icon allowed">' + icon('check') + '</span><div><strong>Run tests and linters</strong><span>Checks run inside the project sandbox.</span></div></div>' +
      '<div class="permission-row"><span class="permission-icon ask">' + icon('shield') + '</span><div><strong>Install packages or use the network</strong><span>Requires your approval for each task.</span></div></div>' +
      '<div class="permission-row"><span class="permission-icon denied">' + icon('x') + '</span><div><strong>Merge, deploy, or change settings</strong><span>Never allowed from a task.</span></div></div>' +
      '</section>' +
      '<h2 class="list-heading">Demo data</h2>' +
      '<section class="card settings-list">' +
      '<div class="setting-row"><span class="setting-label">Storage</span><span class="setting-value muted-value">Saved on this device</span></div>' +
      '<div class="setting-row"><span class="setting-label">Workspace</span><span class="setting-value muted-value">Simulated · no code runs</span></div>' +
      '<div class="setting-row"><span class="setting-label">Local inference</span><span class="setting-value muted-value">Preview · simulated</span></div>' +
      '<button class="settings-button danger-text" data-action="reset-demo">' + icon('trash') + 'Reset demo data</button>' +
      '</section>' +
      '<h2 class="list-heading">Account</h2>' +
      '<section class="card settings-list">' +
      '<div class="account-row"><span class="avatar static">' + esc(data.user.name.charAt(0)) + '</span>' +
      '<div class="account-copy"><strong>' + esc(data.user.name) + '</strong><span>' + esc(data.user.email) + ' · demo account</span></div>' +
      '<button class="btn btn-outline small" data-action="sign-out">Sign out</button></div>' +
      '</section>' +
      '<section class="about-block"><span class="wordmark small">AgentDeck</span>' +
      '<p>MVP prototype · v0.1.0. Task execution, diffs, validation, and local inference are simulated. No repository is modified.</p></section>' +
      '<div class="bottom-space"></div></main>' + AD.tabbar('settings') + '</div>';
  };

  AD.getNewTaskDraft = function () {
    const ui = AD.state.ui;
    if (!ui.newTask) {
      ui.newTask = {
        title: '',
        prompt: '',
        projectId: AD.activeProject().id,
        branch: '',
        branchTouched: false,
        agentId: AD.defaultModelId(),
        error: '',
        errorKind: ''
      };
    }
    return ui.newTask;
  };

  AD.screenNew = function () {
    const draft = AD.getNewTaskDraft();
    const project = AD.getProject(draft.projectId) || AD.activeProject();
    const draftModel = AD.agent(draft.agentId);
    const local = AD.isLocalModel(draft.agentId);
    const modelState = AD.modelState(draftModel);
    const modelReady = AD.modelReady(draft.agentId);
    const hostName = AD.state.data.host.name;
    const online = AD.canReachHost();
    const projectOptions = AD.state.data.projects.map(function (item) {
      return '<option value="' + esc(item.id) + '"' + (item.id === draft.projectId ? ' selected' : '') + '>' + esc(item.name) + '</option>';
    }).join('');
    const branch = draft.branch || 'feature/' + (AD.slugify(draft.title) || 'new-task');
    return '<div class="screen new-task-screen">' +
      '<header class="topbar">' +
      '<button class="icon-btn" data-action="back" aria-label="Back">' + icon('chevron-left') + '</button>' +
      '<div class="topbar-title"><h1>New task</h1><p>Give the agent a clear goal.</p></div></header>' +
      '<main id="main" class="screen-body" data-body="1">' +
      (!online ? '<div class="banner ' + (local ? 'local' : 'warn') + ' compact">' + icon(local ? 'phone' : 'cloud-off') +
        '<div class="banner-copy"><strong>Host offline</strong><span>' +
        (local ? 'This local task can be created and chat on this phone. File changes and checks require ' + esc(hostName) + '.' :
          'You can prepare the task; it cannot start until the host reconnects.') +
        '</span></div>' +
        '<button class="banner-action" data-action="reconnect-host"' + (AD.state.ui.connecting ? ' disabled' : '') + '>' +
        (AD.state.ui.connecting ? 'Connecting…' : 'Reconnect') + '</button></div>' : '') +
      '<form id="new-task-form" class="task-form' + (AD.state.ui.creating ? ' is-busy' : '') + '" novalidate>' +
      '<div class="field">' +
      '<label for="nt-title">Task</label>' +
      '<input class="input" id="nt-title" name="title" type="text" value="' + esc(draft.title) + '" placeholder="What should the agent do?" autocomplete="off" enterkeyhint="next">' +
      '</div>' +
      '<div class="field">' +
      '<div class="field-label-row"><label for="nt-prompt">Instructions</label><span class="optional">Optional</span></div>' +
      '<textarea class="input textarea" id="nt-prompt" name="prompt" rows="4" placeholder="Add context, constraints, and how the work should be verified." enterkeyhint="enter">' + esc(draft.prompt) + '</textarea>' +
      '<p class="field-hint">A title or instructions are required. New tasks start on a fresh branch.</p>' +
      '</div>' +
      '<div class="field"><label for="nt-project">Project</label><select class="select block" id="nt-project">' + projectOptions + '</select></div>' +
      '<div class="field">' +
      '<label id="nt-model-label">Model</label>' +
      '<button type="button" class="model-select' + (local && !modelReady ? ' is-warning' : '') + '" data-action="model-sheet" aria-haspopup="dialog" aria-label="Model: ' + esc(draftModel.name) + (local ? ', on this phone, ' + esc(modelState.label) : ', host') + '">' +
      '<span class="model-select-icon ' + (local ? 'local' : 'host') + '">' + icon(local ? 'phone' : 'server') + '</span>' +
      '<span class="model-select-copy"><strong>' + esc(draftModel.name) + '</strong>' +
      '<span>' + (local ? esc(AD.modelMeta(draftModel)) + ' · ' + esc(modelState.label) : esc(AD.modelMeta(draftModel))) + '</span></span>' +
      (local ? '<span class="local-chip">' + icon('phone') + 'On this phone</span>' : '') +
      '<span class="model-select-chevron">' + icon('chevron-down') + '</span></button>' +
      '<p class="field-hint">' + (local
        ? (modelReady ? 'Inference runs on this phone. File changes, checks, and approvals still require the host.' : 'This model is ' + esc(modelState.label.toLowerCase()) + '. Load it in Settings before creating the task.')
        : 'Host model · runs on ' + esc(hostName) + '.') + '</p>' +
      '</div>' +
      '<div class="field">' +
      '<label for="nt-branch">Branch</label>' +
      '<input class="input mono-input" id="nt-branch" name="branch" type="text" value="' + esc(branch) + '" spellcheck="false" autocomplete="off">' +
      '<p class="field-hint">Based on ' + esc(project.defaultBranch) + ' in ' + esc(project.name) + '.</p>' +
      '</div>' +
      '<div class="context-card">' +
      '<div class="context-head">' + icon('shield') + '<strong>Standard permissions</strong><span>Read/write project files · run checks</span></div>' +
      '<p>Installing packages or using the network requires your approval during the task. Merging and deploying are not available.</p>' +
      '<div class="context-host">' + icon(local ? 'phone' : 'server') + '<span>Inference: ' + esc(draftModel.name) + (local ? ' · this phone' : ' · host') + '</span></div>' +
      '<div class="context-host">' + icon('terminal') + '<span>Execution: ' + esc(hostName) + ' · ' + (online ? 'connected' : 'offline') + '</span></div>' +
      (local ? '<p class="context-note">' + icon('info') + 'Local chat stays on this phone. The host is only used for file changes and checks.</p>' : '') +
      '</div>' +
      (draft.error ? '<div class="form-error" role="alert">' + icon('alert') + '<span>' + esc(draft.error) + '</span>' +
        (draft.errorKind === 'offline' ? '<button type="button" class="inline-link strong" data-action="reconnect-host">Reconnect host</button>' : '') +
        (draft.errorKind === 'model' ? '<button type="button" class="inline-link strong" data-action="go-settings">Manage models</button>' : '') + '</div>' : '') +
      '</form>' +
      '<div class="bottom-space"></div></main>' +
      '<footer class="sticky-actions">' +
      '<button class="btn btn-dark block-btn" type="submit" form="new-task-form"' + (AD.state.ui.creating ? ' disabled' : '') + '>' +
      (AD.state.ui.creating ? '<span class="spinner"></span>Creating task…' : 'Create task') + '</button>' +
      '<p class="fine center">' + (local ? 'Local chat starts on this phone. File changes and checks require the host.' : 'The task is queued immediately. Nothing is pushed or merged.') + '</p></footer></div>';
  };

  AD.screenSignedOut = function () {
    return '<div class="screen signed-out-screen">' +
      '<main id="main" class="centered-screen" data-body="1">' +
      '<div class="signed-out-card"><span class="wordmark">AgentDeck</span>' +
      '<h1>You are signed out</h1>' +
      '<p>This is a local demo. Signing back in only restores the sample workspace on this device.</p>' +
      '<button class="btn btn-dark block-btn" data-action="sign-in">Continue in demo</button>' +
      '</div></main></div>';
  };

  AD.screenSplash = function () {
    const online = AD.canReachHost();
    return '<div class="screen splash"><div class="splash-inner">' +
      '<span class="wordmark">AgentDeck</span>' +
      '<span class="splash-status"><span class="spinner dark"></span>' + (online ? 'Restoring your workspace…' : 'Loading offline workspace…') + '</span>' +
      '<span class="splash-note">Demo data · no live execution</span>' +
      '</div></div>';
  };

  AD.currentScreen = function () {
    switch (AD.state.route.name) {
      case 'tasks': return AD.screenTasks();
      case 'projects': return AD.screenProjects();
      case 'settings': return AD.screenSettings();
      case 'new': return AD.screenNew();
      case 'signed-out': return AD.screenSignedOut();
      case 'task': return AD.screenTask();
      default: return AD.screenHome();
    }
  };

  AD.render = function () {
    const app = document.getElementById('app');
    if (!app) return;
    const currentBody = document.querySelector('[data-body]');
    const active = document.activeElement;
    const focusId = active && active.id ? active.id : null;
    const selectionStart = active && typeof active.selectionStart === 'number' ? active.selectionStart : null;
    const scrollTop = currentBody ? currentBody.scrollTop : 0;
    const nearBottom = currentBody ? currentBody.scrollHeight - currentBody.scrollTop - currentBody.clientHeight < 90 : true;
    const sameKey = AD.state.lastKey === AD.state.route.key;
    const isChat = AD.state.route.name === 'task' && AD.state.route.params.tab === 'chat';
    app.innerHTML = AD.state.ui.booting ? AD.screenSplash() : AD.currentScreen();
    document.querySelectorAll('.composer-input').forEach(function (input) { AD.autoGrow(input); });
    const newBody = document.querySelector('[data-body]');
    if (newBody) {
      if (AD.state.ui.forceTop) {
        newBody.scrollTop = 0;
      } else if (AD.state.ui.forceBottom || (isChat && (!sameKey || nearBottom))) {
        newBody.scrollTop = newBody.scrollHeight;
      } else if (sameKey) {
        newBody.scrollTop = scrollTop;
      }
    }
    AD.state.ui.forceTop = false;
    AD.state.ui.forceBottom = false;
    AD.state.lastKey = AD.state.route.key;
    if (focusId) {
      const target = document.getElementById(focusId);
      if (target) {
        target.focus({ preventScroll: true });
        if (selectionStart != null && target.setSelectionRange) {
          try { target.setSelectionRange(selectionStart, selectionStart); } catch (error) { /* ignored */ }
        }
        if (target.tagName === 'TEXTAREA') AD.autoGrow(target);
      }
    }
    AD.syncTimeLabels();
  };

  AD.autoGrow = function (textarea) {
    if (!textarea) return;
    textarea.style.height = 'auto';
    textarea.style.height = Math.min(textarea.scrollHeight, 132) + 'px';
  };

  AD.syncTimeLabels = function () {
    document.querySelectorAll('[data-ts]').forEach(function (node) {
      node.textContent = AD.timeAgo(node.getAttribute('data-ts'));
    });
  };

  AD.updateHomeResults = function () {
    const holder = document.getElementById('home-results');
    if (!holder) return;
    const tasks = AD.filterTasks(AD.tasksForProject(AD.activeProject().id), AD.state.ui.homeSearch, 'all');
    holder.innerHTML = AD.taskList(tasks.slice(0, 12), {
      icon: 'search',
      title: 'No matching tasks',
      text: 'Try a different title, project, or branch.',
      action: '<button class="btn btn-outline small" data-action="clear-search" data-target="home-search">Clear search</button>'
    });
    const heading = document.getElementById('recent-heading');
    if (heading) heading.textContent = AD.state.ui.homeSearch ? 'Results' : 'Recent tasks';
  };

  AD.updateTaskResults = function () {
    const holder = document.getElementById('task-results');
    if (!holder) return;
    const tasks = AD.filterTasks(AD.tasksForProject(AD.activeProject().id), AD.state.ui.taskSearch, AD.state.ui.statusFilter);
    holder.innerHTML = AD.taskList(tasks, {
      icon: 'search',
      title: 'No tasks match',
      text: 'Adjust the search or status filter.',
      action: '<button class="btn btn-outline small" data-action="clear-filters">Clear filters</button>'
    });
    const label = document.getElementById('task-count-label');
    if (label) label.textContent = AD.plural(tasks.length, 'task');
  };
})(window.AD);
