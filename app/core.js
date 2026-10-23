window.AD = window.AD || {};

(function (AD) {
  'use strict';

  const STORAGE_KEY = 'agentdeck.demo.v4';

  AD.STATUS = {
    pending: { label: 'Pending', icon: 'clock', tone: 'gray' },
    in_progress: { label: 'In progress', icon: 'spinner', tone: 'blue' },
    needs_input: { label: 'Needs input', icon: 'message', tone: 'violet' },
    review: { label: 'Review', icon: 'eye', tone: 'amber' },
    failed: { label: 'Failed', icon: 'alert', tone: 'red' },
    completed: { label: 'Completed', icon: 'check-circle', tone: 'green' }
  };

  AD.CHECK_META = {
    passed: { label: 'Passed', icon: 'check-circle', tone: 'green' },
    failed: { label: 'Failed', icon: 'x-circle', tone: 'red' },
    skipped: { label: 'Skipped', icon: 'dash-circle', tone: 'gray' },
    not_run: { label: 'Not run', icon: 'circle', tone: 'gray' }
  };

  AD.FILE_META = {
    added: { label: 'Added', icon: 'file-plus', tone: 'green', letter: 'A' },
    deleted: { label: 'Deleted', icon: 'file-minus', tone: 'red', letter: 'D' },
    renamed: { label: 'Renamed', icon: 'arrow-right', tone: 'amber', letter: 'R' },
    modified: { label: 'Modified', icon: 'pencil', tone: 'blue', letter: 'M' }
  };

  AD.ACTIVITY_ICONS = {
    read: 'file',
    edit: 'pencil',
    test: 'terminal',
    status: 'activity',
    review: 'check-circle',
    queue: 'clock',
    check: 'check-circle',
    error: 'alert',
    permission: 'shield',
    connect: 'server',
    local: 'phone'
  };

  AD.state = {
    data: null,
    route: null,
    lastKey: null,
    depth: 0,
    timers: new Map(),
    modelTimers: new Map(),
    ui: {
      booting: true,
      forceBottom: false,
      forceTop: false,
      homeSearch: '',
      taskSearch: '',
      statusFilter: 'all',
      activityOpen: {},
      diffFileId: null,
      newTask: null,
      drafts: {},
      localTyping: {},
      creating: false,
      sending: false,
      connecting: false,
      sheet: null
    }
  };

  AD.esc = function (value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (ch) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
    });
  };

  AD.nl2br = function (value) {
    return AD.esc(value).replace(/\n/g, '<br>');
  };

  AD.icon = function (name, className) {
    return '<svg class="icon ' + (className || '') + '" aria-hidden="true" focusable="false"><use href="#i-' +
      AD.esc(name) + '"></use></svg>';
  };

  AD.plural = function (count, one, many) {
    return count + ' ' + (count === 1 ? one : (many || one + 's'));
  };

  AD.timeAgo = function (isoDate) {
    const seconds = Math.max(0, (Date.now() - new Date(isoDate).getTime()) / 1000);
    if (seconds < 45) return 'just now';
    if (seconds < 90) return '1m ago';
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return minutes + 'm ago';
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return hours + 'h ago';
    const days = Math.floor(hours / 24);
    if (days < 7) return days + 'd ago';
    return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric' }).format(new Date(isoDate));
  };

  AD.clockTime = function (isoDate) {
    return new Intl.DateTimeFormat('en', { hour: 'numeric', minute: '2-digit' }).format(new Date(isoDate));
  };

  AD.fullDate = function (isoDate) {
    return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(isoDate));
  };

  AD.slugify = function (value) {
    return String(value || '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 36);
  };

  AD.newId = function (prefix) {
    return prefix + '_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  };

  AD.load = function () {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (parsed && parsed.meta && parsed.meta.version === 4 && Array.isArray(parsed.tasks)) {
          return parsed;
        }
      }
    } catch (error) {
      /* Storage may be unavailable in private or file:// contexts. */
    }
    return window.AgentDeckSeed();
  };

  AD.save = function () {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(AD.state.data));
    } catch (error) {
      /* The demo still works in memory when storage is unavailable. */
    }
  };

  AD.resetData = function () {
    AD.clearTimers();
    AD.state.modelTimers.forEach(function (timer) { clearTimeout(timer); });
    AD.state.modelTimers.clear();
    AD.state.data = window.AgentDeckSeed();
    AD.state.ui.diffFileId = null;
    AD.state.ui.newTask = null;
    AD.state.ui.drafts = {};
    AD.state.ui.activityOpen = {};
    AD.state.ui.localTyping = {};
    AD.state.ui.statusFilter = 'all';
    AD.state.ui.homeSearch = '';
    AD.state.ui.taskSearch = '';
    AD.state.ui.creating = false;
    AD.state.ui.sending = false;
    AD.state.ui.connecting = false;
    AD.save();
  };

  AD.parseRoute = function () {
    const raw = location.hash.replace(/^#\/?/, '');
    const parts = raw.split('/').filter(Boolean).map(function (part) {
      try { return decodeURIComponent(part); } catch (error) { return part; }
    });
    if (!parts.length) return { name: 'home', params: {}, key: 'home' };
    if (parts[0] === 'task' && parts[1]) {
      const tab = ['chat', 'changes', 'checks'].includes(parts[2]) ? parts[2] : 'chat';
      return { name: 'task', params: { id: parts[1], tab: tab }, key: 'task/' + parts[1] + '/' + tab };
    }
    if (['home', 'tasks', 'projects', 'settings', 'new', 'signed-out'].includes(parts[0])) {
      return { name: parts[0], params: {}, key: parts[0] };
    }
    return { name: 'home', params: {}, key: 'home' };
  };

  AD.getTask = function (id) {
    return AD.state.data.tasks.find(function (task) { return task.id === id; }) || null;
  };

  AD.getProject = function (id) {
    return AD.state.data.projects.find(function (project) { return project.id === id; }) || null;
  };

  AD.activeProject = function () {
    return AD.getProject(AD.state.data.settings.activeProjectId) || AD.state.data.projects[0];
  };

  AD.agent = function (id) {
    return AD.state.data.agents.find(function (agent) { return agent.id === id; }) || AD.state.data.agents[0];
  };

  AD.agentName = function (id) {
    return AD.agent(id).name;
  };

  AD.localModels = function () {
    return AD.state.data.agents.filter(function (model) { return model.provider === 'local'; });
  };

  AD.hostModels = function () {
    return AD.state.data.agents.filter(function (model) { return model.provider !== 'local'; });
  };

  AD.isLocalModel = function (id) {
    const model = AD.state.data.agents.find(function (item) { return item.id === id; });
    return Boolean(model && model.provider === 'local');
  };

  AD.isLocalTask = function (task) {
    return Boolean(task && AD.isLocalModel(task.agentId));
  };

  AD.modelReady = function (id) {
    const model = AD.state.data.agents.find(function (item) { return item.id === id; });
    if (!model) return false;
    return model.provider !== 'local' || model.state === 'ready';
  };

  AD.modelState = function (model) {
    if (!model) return { label: 'Unavailable', tone: 'gray' };
    if (model.provider !== 'local') return { label: 'Host', tone: 'gray' };
    if (model.state === 'ready') return { label: 'Ready', tone: 'green' };
    if (model.state === 'loading') return { label: 'Loading…', tone: 'blue' };
    if (model.state === 'importing') return { label: 'Importing…', tone: 'blue' };
    if (model.state === 'unloaded') return { label: 'Not loaded', tone: 'gray' };
    return { label: 'Not imported', tone: 'gray' };
  };

  AD.modelMeta = function (model) {
    if (!model) return '';
    if (model.provider === 'local') {
      return model.quantization + ' · ' + model.sizeGB + ' GB · ' + model.contextK + 'K context';
    }
    return (model.providerLabel || 'Host') + ' · host';
  };

  AD.defaultModelId = function () {
    const preferred = AD.state.data.settings.defaultAgentId;
    if (AD.modelReady(preferred)) return preferred;
    const fallback = AD.state.data.agents.find(function (model) {
      return model.provider !== 'local' && AD.modelReady(model.id);
    }) || AD.state.data.agents.find(function (model) { return AD.modelReady(model.id); });
    return fallback ? fallback.id : preferred;
  };

  AD.tasksForProject = function (projectId) {
    return AD.state.data.tasks
      .filter(function (task) { return task.projectId === projectId; })
      .sort(function (a, b) { return new Date(b.updatedAt) - new Date(a.updatedAt); });
  };

  AD.statusCounts = function (tasks) {
    const counts = { pending: 0, in_progress: 0, needs_input: 0, review: 0, failed: 0, completed: 0 };
    tasks.forEach(function (task) { counts[task.status] = (counts[task.status] || 0) + 1; });
    return counts;
  };

  AD.matchStatus = function (task, filter) {
    if (filter === 'all') return true;
    if (filter === 'active') return task.status === 'in_progress';
    return task.status === filter;
  };

  AD.filterTasks = function (tasks, query, filter) {
    const term = query.trim().toLowerCase();
    return tasks.filter(function (task) {
      if (!AD.matchStatus(task, filter)) return false;
      if (!term) return true;
      const project = AD.getProject(task.projectId);
      return [task.title, task.prompt, task.branch, project ? project.name : '', AD.agentName(task.agentId)]
        .join(' ')
        .toLowerCase()
        .includes(term);
    });
  };

  AD.canReachHost = function () {
    return AD.state.data.host.state === 'connected';
  };

  AD.fileStats = function (file) {
    if (!file.diff) return { additions: file.additions || 0, deletions: file.deletions || 0 };
    return file.diff.reduce(function (totals, line) {
      if (line.type === '+') totals.additions += 1;
      if (line.type === '-') totals.deletions += 1;
      return totals;
    }, { additions: 0, deletions: 0 });
  };

  AD.changeTotals = function (files) {
    return files.reduce(function (totals, file) {
      const stats = AD.fileStats(file);
      totals.additions += stats.additions;
      totals.deletions += stats.deletions;
      return totals;
    }, { additions: 0, deletions: 0 });
  };

  AD.checkSummary = function (checks) {
    const counts = { passed: 0, failed: 0, skipped: 0, not_run: 0 };
    checks.forEach(function (check) { counts[check.status] = (counts[check.status] || 0) + 1; });
    let label = 'No checks run';
    let tone = 'gray';
    if (counts.failed) {
      label = AD.plural(counts.failed, 'check') + ' failed';
      tone = 'red';
    } else if (counts.passed) {
      label = counts.passed + ' passed';
      if (counts.not_run) label += ' · ' + counts.not_run + ' not run';
      if (counts.skipped) label += ' · ' + counts.skipped + ' skipped';
      tone = 'green';
    } else if (counts.skipped || counts.not_run) {
      label = 'Checks not run';
      tone = 'gray';
    }
    return { counts: counts, label: label, tone: tone };
  };

  AD.latestDecision = function (task) {
    return task.decisions && task.decisions.length ? task.decisions[0] : null;
  };

  AD.clearTimers = function () {
    AD.state.timers.forEach(function (timer) { clearTimeout(timer); });
    AD.state.timers.clear();
  };

  AD.announce = function (message) {
    const live = document.getElementById('live');
    if (!live) return;
    live.textContent = '';
    window.setTimeout(function () { live.textContent = message; }, 40);
  };
})(window.AD);
