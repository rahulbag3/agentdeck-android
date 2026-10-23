(function (AD) {
  'use strict';

  const nowIso = function () { return new Date().toISOString(); };

  AD.toast = function (message) {
    const element = document.getElementById('toast');
    if (!element) return;
    element.textContent = message;
    element.classList.add('show');
    window.clearTimeout(AD.state.toastTimer);
    AD.state.toastTimer = window.setTimeout(function () {
      element.classList.remove('show');
    }, 3200);
  };

  function addMessage(task, role, text, state) {
    const message = {
      id: AD.newId('m'),
      role: role,
      text: text,
      at: nowIso(),
      state: state || 'delivered'
    };
    task.messages.push(message);
    return message;
  }

  function addEvent(task, type, text) {
    task.activity.push({ id: AD.newId('e'), type: type, text: text, at: nowIso() });
  }

  function setPlan(task, states) {
    if (!task.plan) return;
    task.plan.forEach(function (step, index) {
      if (states[index]) step.state = states[index];
    });
  }

  function activateNextStep(task) {
    if (!task.plan) return;
    for (let index = 0; index < task.plan.length; index += 1) {
      if (task.plan[index].state !== 'done') {
        task.plan[index].state = 'active';
        return;
      }
    }
  }

  function deliverQueued(task) {
    task.messages.forEach(function (message) {
      if (message.state === 'queued') message.state = 'delivered';
    });
  }

  function makeFile(path, status, lines, oldStart, newStart, language) {
    const diff = window.AgentDeckKit.mkDiff(oldStart, newStart, lines);
    return {
      id: AD.newId('f'),
      path: path,
      oldPath: '',
      status: status,
      additions: diff.filter(function (line) { return line.type === '+'; }).length,
      deletions: diff.filter(function (line) { return line.type === '-'; }).length,
      language: language || 'TypeScript',
      diff: diff,
      unavailable: false,
      unavailableReason: ''
    };
  }

  function makeDemoFiles(task) {
    const slug = AD.slugify(task.title) || 'change';
    const rawCamel = slug.replace(/-([a-z0-9])/g, function (match, letter) { return letter.toUpperCase(); });
    const camel = /^[0-9]/.test(rawCamel) ? 'task' + rawCamel.charAt(0).toUpperCase() + rawCamel.slice(1) : rawCamel;
    const pascal = camel.charAt(0).toUpperCase() + camel.slice(1);
    if (task.projectId === 'docs-site') {
      return [
        makeFile('docs/guides/' + slug + '.md', 'added', [
          '+# ' + task.title,
          '+',
          '+## Overview',
          '+',
          '+This guide was added as part of a small documentation change.',
          '+It explains the new behavior and how to verify it.',
          '+',
          '+## Steps',
          '+',
          '+1. Open the project workspace.',
          '+2. Follow the documented command.',
          '+3. Confirm the expected result.',
          '+',
          '+## Verification',
          '+',
          '+Run the documentation checks before requesting review.'
        ], 0, 1, 'Markdown'),
        makeFile('docs/index.md', 'modified', [
          ' # Documentation',
          ' ',
          ' ## Guides',
          '+',
          '+### [' + task.title + '](/docs/guides/' + slug + ')',
          '+',
          '+- What the change does',
          '+- How to verify it'
        ], 1, 1, 'Markdown')
      ];
    }
    return [
      makeFile('src/features/' + slug + '.ts', 'added', [
        '+export type ' + pascal + 'Options = {',
        '+  enabled: boolean;',
        '+  limit?: number;',
        '+};',
        '+',
        '+export function ' + camel + '(options: ' + pascal + 'Options) {',
        '+  const limit = Math.min(options.limit ?? 50, 100);',
        '+  if (!options.enabled) return [];',
        '+  return { ok: true, limit };',
        '+}'
      ], 0, 1, 'TypeScript'),
      makeFile('src/routes/' + slug + '.ts', 'modified', [
        ' import { Router } from "express";',
        '+import { ' + camel + ' } from "../features/' + slug + '";',
        ' ',
        ' const router = Router();',
        ' ',
        '+router.get("/' + slug + '", (_req, res) => {',
        '+  res.json(' + camel + '({ enabled: true }));',
        '+});',
        '+',
        ' export default router;'
      ], 1, 1, 'TypeScript'),
      makeFile('tests/' + slug + '.test.ts', 'added', [
        '+import { describe, expect, it } from "vitest";',
        '+import { ' + camel + ' } from "../src/features/' + slug + '";',
        '+',
        '+describe("' + camel + '", () => {',
        '+  it("returns nothing when disabled", () => {',
        '+    expect(' + camel + '({ enabled: false })).toEqual([]);',
        '+  });',
        '+',
        '+  it("caps the limit", () => {',
        '+    expect(' + camel + '({ enabled: true, limit: 500 })).toEqual({ ok: true, limit: 100 });',
        '+  });',
        '+});'
      ], 0, 1, 'TypeScript')
    ];
  }

  function makeDemoChecks(task) {
    const at = nowIso();
    if (task.projectId === 'docs-site') {
      return [
        { id: AD.newId('c'), type: 'custom', name: 'Link check', status: 'passed', summary: '42 links checked', at: at, command: 'pnpm check:links', duration: '1.8s', output: '42 links checked\n0 broken links' },
        { id: AD.newId('c'), type: 'lint', name: 'Markdown lint', status: 'passed', summary: '0 problems', at: at, command: 'pnpm lint:docs', duration: '0.9s', output: 'No issues found.' },
        { id: AD.newId('c'), type: 'build', name: 'Build', status: 'not_run', summary: 'No build script configured', at: at, command: '—', duration: '', output: 'This project has no build script, so nothing was run.' }
      ];
    }
    return [
      { id: AD.newId('c'), type: 'test', name: 'Unit tests', status: 'passed', summary: '24 passed · 0 failed', at: at, command: 'pnpm test', duration: '2.8s', output: 'Test Files  3 passed (3)\nTests       24 passed (24)' },
      { id: AD.newId('c'), type: 'lint', name: 'Lint', status: 'passed', summary: '0 problems', at: at, command: 'pnpm lint', duration: '1.4s', output: 'No issues found.' },
      { id: AD.newId('c'), type: 'typecheck', name: 'Type check', status: 'passed', summary: 'No type errors', at: at, command: 'pnpm typecheck', duration: '2.1s', output: 'No errors.' },
      { id: AD.newId('c'), type: 'build', name: 'Build', status: 'not_run', summary: 'No build script configured', at: at, command: '—', duration: '', output: 'This project has no build script, so nothing was run.' }
    ];
  }

  function checksPassedSentence(task) {
    const passed = task.checks.filter(function (check) { return check.status === 'passed'; }).length;
    return 'Ran checks — ' + passed + ' passed';
  }

  function fixRetriedClient(task) {
    const client = task.changedFiles.find(function (file) { return file.path === 'src/api/client.ts'; });
    if (!client) return;
    const diff = window.AgentDeckKit.mkDiff(38, 38, [
      '   try {',
      '-    return normalize(error);',
      '+    const mapped = toRequestError(error);',
      '+    return normalize(mapped);',
      '   } catch {',
      '+    return new RequestError("Upstream request failed", 502, true);',
      '   }'
    ]);
    client.diff = diff;
    client.unavailable = false;
    client.unavailableReason = '';
    client.additions = diff.filter(function (line) { return line.type === '+'; }).length;
    client.deletions = diff.filter(function (line) { return line.type === '-'; }).length;
  }

  AD.clearTaskTimer = function (taskId) {
    const timer = AD.state.timers.get(taskId);
    if (timer) {
      clearTimeout(timer);
      AD.state.timers.delete(taskId);
    }
  };

  function schedule(taskId, delay) {
    AD.clearTaskTimer(taskId);
    if (!AD.canReachHost()) return;
    const timer = window.setTimeout(function () {
      AD.state.timers.delete(taskId);
      simulateStep(taskId);
    }, delay);
    AD.state.timers.set(taskId, timer);
  }

  AD.startSimulation = function (taskId, kind, delay) {
    const task = AD.getTask(taskId);
    if (!task) return;
    task.sim = { kind: kind, step: 0 };
    AD.save();
    schedule(taskId, delay == null ? 1600 : delay);
  };

  AD.resumeSimulations = function () {
    if (!AD.canReachHost()) return;
    AD.state.data.tasks.forEach(function (task) {
      if (task.sim && ['pending', 'in_progress', 'needs_input'].includes(task.status)) {
        schedule(task.id, 2400);
      }
    });
  };

  const SIM_LENGTH = { new: 3, retry: 2, revise: 2, resume: 2 };

  function simulateStep(taskId) {
    const task = AD.getTask(taskId);
    if (!task || !task.sim || !AD.canReachHost()) return;
    const sim = task.sim;
    const host = AD.state.data.host;
    let delay = 3400;

    if (sim.kind === 'new') {
      if (sim.step === 0) {
        task.status = 'in_progress';
        deliverQueued(task);
        addMessage(task, 'agent', "I'll read the project structure and the files involved first.");
        addEvent(task, 'status', 'Started on ' + host.name);
        setPlan(task, ['done', 'active', 'todo', 'todo']);
        delay = 3600;
      } else if (sim.step === 1) {
        task.changedFiles = makeDemoFiles(task);
        addEvent(task, 'edit', 'Edited ' + AD.plural(task.changedFiles.length, 'file'));
        addMessage(task, 'agent', 'Plan:\n1. Inspect the current implementation\n2. Make the smallest complete change\n3. Run the configured checks\n\nThe first pass is in place. Running checks now.');
        setPlan(task, ['done', 'done', 'active', 'todo']);
        delay = 3800;
      } else {
        task.checks = makeDemoChecks(task);
        task.status = 'review';
        addMessage(task, 'agent', 'The change is implemented and the checks that ran pass. The changes are ready for your review.');
        addEvent(task, 'test', checksPassedSentence(task));
        addEvent(task, 'status', 'Moved to review');
        setPlan(task, ['done', 'done', 'done', 'done']);
      }
    } else if (sim.kind === 'retry') {
      if (sim.step === 0) {
        task.status = 'in_progress';
        task.failure = null;
        task.checks = [];
        addMessage(task, 'agent', "Retrying from the type-check failure. I'll fix the unknown type in the client and re-run the checks.");
        addEvent(task, 'status', 'Retry started');
        setPlan(task, ['done', 'done', 'active', 'todo']);
        delay = 3600;
      } else {
        fixRetriedClient(task);
        task.checks = makeDemoChecks(task);
        task.status = 'review';
        addMessage(task, 'agent', 'Fixed the type guard and re-ran the checks. The checks that ran now pass.');
        addEvent(task, 'test', checksPassedSentence(task));
        addEvent(task, 'status', 'Moved to review');
        setPlan(task, ['done', 'done', 'done', 'done']);
      }
    } else if (sim.kind === 'revise') {
      if (sim.step === 0) {
        task.status = 'in_progress';
        task.checks = [];
        addMessage(task, 'agent', "Picked up your feedback. I'll update the implementation and re-run the checks.");
        addEvent(task, 'status', 'Reopened for changes');
        activateNextStep(task);
        delay = 3800;
      } else {
        if (!task.changedFiles.length) task.changedFiles = makeDemoFiles(task);
        task.checks = makeDemoChecks(task);
        task.status = 'review';
        addMessage(task, 'agent', 'Updated the task and re-ran the checks. Ready for another look.');
        addEvent(task, 'test', checksPassedSentence(task));
        addEvent(task, 'status', 'Moved to review');
        setPlan(task, ['done', 'done', 'done', 'done']);
      }
    } else if (sim.kind === 'resume') {
      if (sim.step === 0) {
        task.status = 'in_progress';
        task.permissionRequest = null;
        addMessage(task, 'agent', 'Installed the package and wired up the limiter. Running the checks now.');
        addEvent(task, 'edit', 'Added rate limiter middleware');
        setPlan(task, ['done', 'done', 'active', 'todo']);
        delay = 4000;
      } else {
        task.changedFiles = makeDemoFiles(task);
        task.checks = makeDemoChecks(task);
        task.status = 'review';
        addMessage(task, 'agent', 'Rate limiting is implemented and the checks pass. Ready for your review.');
        addEvent(task, 'test', checksPassedSentence(task));
        addEvent(task, 'status', 'Moved to review');
        setPlan(task, ['done', 'done', 'done', 'done']);
      }
    }

    task.updatedAt = nowIso();
    const nextStep = sim.step + 1;
    const max = SIM_LENGTH[sim.kind] || 1;
    if (nextStep < max) {
      task.sim.step = nextStep;
      AD.save();
      AD.render();
      schedule(taskId, delay);
    } else {
      task.sim = null;
      AD.save();
      AD.render();
      if (task.status === 'review') {
        AD.announce(task.title + ' is ready for review');
        if (!(AD.state.route.name === 'task' && AD.state.route.params.id === task.id)) {
          AD.toast('“' + task.title + '” is ready for review');
        }
      }
    }
  }

  function localReplyText(task, prompt) {
    const context = (task.title + ' ' + task.prompt + ' ' + prompt).toLowerCase();
    if (/type[- ]?check|client\.ts|normalize/.test(context)) {
      return 'I can outline the patch locally. Applying it and running checks still needs the execution host. The minimal fix is to narrow the unknown value with toRequestError(error) before normalize().';
    }
    if (/test/.test(context)) {
      return 'I can review the test cases here on the phone, but running the suite is a host action.';
    }
    if (/(patch|fix|edit|change)/.test(String(prompt || '').toLowerCase())) {
      return 'I can outline the change locally. Applying it and running checks still needs the execution host.';
    }
    return "I'm running on this phone. I can discuss this task and review code here; file changes and validation stay with the execution host.";
  }

  AD.startLocalResponse = function (taskId, promptText) {
    const task = AD.getTask(taskId);
    if (!task || !AD.isLocalTask(task) || !AD.modelReady(task.agentId)) return;
    if (AD.state.ui.localTyping[taskId]) return;
    AD.state.ui.localTyping[taskId] = true;
    AD.state.ui.forceBottom = AD.state.route.name === 'task' && AD.state.route.params.id === taskId;
    AD.render();
    window.setTimeout(function () {
      if (!AD.state.ui.localTyping[taskId]) return;
      const current = AD.getTask(taskId);
      if (!current) {
        delete AD.state.ui.localTyping[taskId];
        return;
      }
      delete AD.state.ui.localTyping[taskId];
      const stamp = nowIso();
      current.messages.push({
        id: AD.newId('m'),
        role: 'agent',
        text: localReplyText(current, promptText),
        at: stamp,
        state: 'delivered',
        local: true,
        preview: true
      });
      current.updatedAt = stamp;
      addEvent(current, 'local', 'Answered on this phone (preview)');
      AD.state.ui.sending = false;
      AD.save();
      AD.state.ui.forceBottom = AD.state.route.name === 'task' && AD.state.route.params.id === taskId;
      AD.render();
      AD.announce('Local reply ready');
    }, 1600);
  };

  AD.sendLocalMessage = function (taskId, body) {
    const task = AD.getTask(taskId);
    if (!task || !AD.isLocalTask(task)) return;
    if (!AD.modelReady(task.agentId)) {
      AD.toast('Load the local model in Settings before sending');
      return;
    }
    if (AD.state.ui.localTyping[taskId]) return;
    const stamp = nowIso();
    task.messages.push({ id: AD.newId('m'), role: 'user', text: body, at: stamp, state: 'sending' });
    task.updatedAt = stamp;
    AD.state.ui.drafts[taskId] = '';
    AD.state.ui.sending = true;
    AD.save();
    AD.state.ui.forceBottom = AD.state.route.name === 'task' && AD.state.route.params.id === taskId;
    AD.render();
    const messageId = task.messages[task.messages.length - 1].id;
    window.setTimeout(function () {
      const current = AD.getTask(taskId);
      if (!current) {
        AD.state.ui.sending = false;
        return;
      }
      const message = current.messages.find(function (item) { return item.id === messageId; });
      if (!message) return;
      message.state = 'delivered';
      current.updatedAt = nowIso();
      AD.save();
      AD.state.ui.forceBottom = AD.state.route.name === 'task' && AD.state.route.params.id === taskId;
      AD.render();
      AD.startLocalResponse(taskId, body);
    }, 450);
  };

  AD.createTaskRecord = function (fields) {
    const stamp = nowIso();
    const local = AD.isLocalModel(fields.agentId);
    const task = {
      id: AD.newId('t'),
      title: fields.title,
      prompt: fields.prompt,
      projectId: fields.projectId,
      branch: fields.branch,
      agentId: fields.agentId,
      status: local ? 'needs_input' : 'pending',
      createdAt: stamp,
      updatedAt: stamp,
      messages: [{ id: AD.newId('m'), role: 'user', text: fields.prompt, at: stamp, state: 'delivered' }],
      activity: [{ id: AD.newId('e'), type: local ? 'local' : 'queue', text: local ? 'Ready on this phone (preview)' : 'Queued for execution', at: stamp }],
      changedFiles: [],
      checks: [],
      decisions: [],
      permissionRequest: null,
      failure: null,
      plan: local ? null : [
        { text: 'Read project structure', state: 'todo' },
        { text: 'Make the change', state: 'todo' },
        { text: 'Run checks', state: 'todo' },
        { text: 'Prepare review', state: 'todo' }
      ],
      sim: null
    };
    AD.state.data.tasks.unshift(task);
    if (local) {
      AD.save();
      AD.startLocalResponse(task.id, fields.prompt);
    } else {
      AD.startSimulation(task.id, 'new', 2000);
      AD.save();
    }
    return task;
  };

  AD.completeDelivery = function (taskId, messageId) {
    const task = AD.getTask(taskId);
    AD.state.ui.sending = false;
    if (!task) return;
    const message = task.messages.find(function (item) { return item.id === messageId; });
    if (!message) return;
    if (!AD.canReachHost()) {
      message.state = 'failed';
      AD.save();
      AD.render();
      AD.toast('Host went offline · message not sent');
      return;
    }
    message.state = task.status === 'pending' ? 'queued' : 'delivered';
    let reopened = false;
    if (task.status === 'review') {
      task.status = 'in_progress';
      task.updatedAt = nowIso();
      addEvent(task, 'status', 'Reopened by your message');
      reopened = true;
    } else if (task.status === 'needs_input' && !(task.permissionRequest && task.permissionRequest.state === 'pending')) {
      task.status = 'in_progress';
      task.updatedAt = nowIso();
      addEvent(task, 'status', 'Continued after your reply');
      reopened = true;
    }
    if (reopened) {
      AD.startSimulation(task.id, 'revise', 1800);
    } else {
      task.updatedAt = nowIso();
      AD.save();
    }
    AD.state.ui.forceBottom = true;
    AD.render();
    AD.announce(reopened ? 'Message delivered. Task reopened.' : 'Message delivered');
  };

  AD.sendMessage = function (taskId, text) {
    const task = AD.getTask(taskId);
    const body = String(text || '').trim();
    if (!task || !body || task.status === 'completed') return;
    if (AD.isLocalTask(task)) {
      AD.sendLocalMessage(taskId, body);
      return;
    }
    const stamp = nowIso();
    if (!AD.canReachHost()) {
      task.messages.push({ id: AD.newId('m'), role: 'user', text: body, at: stamp, state: 'failed' });
      task.updatedAt = stamp;
      AD.save();
      AD.state.ui.forceBottom = true;
      AD.render();
      AD.toast('Host offline · message not sent');
      return;
    }
    task.messages.push({ id: AD.newId('m'), role: 'user', text: body, at: stamp, state: 'sending' });
    task.updatedAt = stamp;
    AD.state.ui.drafts[taskId] = '';
    AD.state.ui.sending = true;
    AD.save();
    AD.state.ui.forceBottom = true;
    AD.render();
    const messageId = task.messages[task.messages.length - 1].id;
    window.setTimeout(function () {
      AD.completeDelivery(taskId, messageId);
    }, 620);
  };

  AD.retryMessage = function (taskId, messageId) {
    const task = AD.getTask(taskId);
    if (!task) return;
    const message = task.messages.find(function (item) { return item.id === messageId; });
    if (!message) return;
    if (AD.isLocalTask(task)) {
      if (!AD.modelReady(task.agentId)) {
        AD.toast('Load the local model in Settings before retrying');
        return;
      }
      if (AD.state.ui.localTyping[task.id]) return;
      message.state = 'sending';
      AD.state.ui.sending = true;
      AD.save();
      AD.render();
      window.setTimeout(function () {
        const current = AD.getTask(taskId);
        if (!current) {
          AD.state.ui.sending = false;
          return;
        }
        const currentMessage = current.messages.find(function (item) { return item.id === messageId; });
        if (!currentMessage) return;
        currentMessage.state = 'delivered';
        current.updatedAt = nowIso();
        AD.save();
        AD.state.ui.forceBottom = AD.state.route.name === 'task' && AD.state.route.params.id === taskId;
        AD.render();
        AD.startLocalResponse(taskId, currentMessage.text);
      }, 560);
      return;
    }
    if (!AD.canReachHost()) {
      AD.toast('Reconnect Studio Mac before retrying');
      return;
    }
    message.state = 'sending';
    AD.state.ui.sending = true;
    AD.save();
    AD.render();
    window.setTimeout(function () {
      AD.completeDelivery(taskId, messageId);
    }, 560);
  };

  AD.retryTask = function (taskId) {
    const task = AD.getTask(taskId);
    if (!task || task.status !== 'failed') return;
    if (AD.isLocalTask(task)) {
      AD.toast('Local tasks are chat-only in this preview');
      return;
    }
    if (!AD.canReachHost()) {
      AD.toast('Reconnect Studio Mac before retrying');
      return;
    }
    task.status = 'in_progress';
    task.failure = null;
    task.updatedAt = nowIso();
    addEvent(task, 'status', 'Retry requested');
    AD.startSimulation(task.id, 'retry', 1600);
    AD.state.ui.forceBottom = true;
    AD.render();
    AD.toast('Retry started');
  };

  AD.allowPermission = function (taskId) {
    const task = AD.getTask(taskId);
    if (!task || !task.permissionRequest || task.permissionRequest.state !== 'pending') return;
    if (!AD.canReachHost()) {
      AD.toast('Reconnect Studio Mac before approving');
      return;
    }
    const command = task.permissionRequest.command;
    task.permissionRequest.state = 'allowed';
    task.status = 'in_progress';
    task.updatedAt = nowIso();
    addEvent(task, 'permission', 'You allowed: ' + command);
    AD.startSimulation(task.id, 'resume', 1600);
    AD.state.ui.forceBottom = true;
    AD.render();
    AD.toast('Permission granted for this task only');
  };

  AD.declinePermission = function (taskId) {
    const task = AD.getTask(taskId);
    if (!task || !task.permissionRequest) return;
    if (!AD.canReachHost()) {
      AD.toast('Reconnect Studio Mac before responding');
      return;
    }
    task.permissionRequest = null;
    task.status = 'needs_input';
    task.updatedAt = nowIso();
    addEvent(task, 'permission', 'You declined the install request');
    addMessage(task, 'agent', "Understood. I won't install anything. If you have another approach, tell me and I'll continue.");
    AD.save();
    AD.state.ui.forceBottom = true;
    AD.render();
    AD.toast('Request declined');
  };

  AD.approveTask = function (taskId) {
    const task = AD.getTask(taskId);
    if (!task) return { ok: false, error: 'This task is unavailable.' };
    if (!AD.canReachHost()) return { ok: false, error: 'Studio Mac is offline. Reconnect before recording a decision.' };
    if (task.status !== 'review') return { ok: false, error: 'This task is not waiting for review.' };
    const stamp = nowIso();
    task.status = 'completed';
    task.updatedAt = stamp;
    task.decisions.unshift({ id: AD.newId('d'), decision: 'approved', feedback: '', by: 'You', at: stamp });
    addEvent(task, 'review', 'You approved this work');
    AD.save();
    return { ok: true, task: task };
  };

  AD.requestChanges = function (taskId, feedback) {
    const task = AD.getTask(taskId);
    const note = String(feedback || '').trim();
    if (!task) return { ok: false, error: 'This task is unavailable.' };
    if (!AD.canReachHost()) return { ok: false, error: 'Studio Mac is offline. Reconnect before sending feedback.' };
    if (task.status !== 'review') return { ok: false, error: 'This task is not waiting for review.' };
    if (!note) return { ok: false, error: 'Add feedback before sending.' };
    const stamp = nowIso();
    task.status = 'in_progress';
    task.updatedAt = stamp;
    task.decisions.unshift({ id: AD.newId('d'), decision: 'changes_requested', feedback: note, by: 'You', at: stamp });
    task.messages.push({ id: AD.newId('m'), role: 'user', text: note, at: stamp, state: 'delivered' });
    addEvent(task, 'review', 'You requested changes');
    AD.startSimulation(task.id, 'revise', 1800);
    return { ok: true, task: task };
  };

  function setModelTimer(modelId, fn, delay) {
    const existing = AD.state.modelTimers.get(modelId);
    if (existing) clearTimeout(existing);
    const timer = window.setTimeout(function () {
      AD.state.modelTimers.delete(modelId);
      fn();
    }, delay);
    AD.state.modelTimers.set(modelId, timer);
  }

  AD.loadLocalModel = function (modelId) {
    const model = AD.state.data.agents.find(function (item) { return item.id === modelId; });
    if (!model || model.provider !== 'local' || model.state !== 'unloaded') return;
    model.state = 'loading';
    AD.save();
    AD.render();
    setModelTimer(modelId, function () {
      const current = AD.state.data.agents.find(function (item) { return item.id === modelId; });
      if (!current || current.state !== 'loading') return;
      current.state = 'ready';
      current.loaded = true;
      AD.save();
      AD.render();
      AD.toast(current.name + ' loaded · preview');
      AD.announce(current.name + ' is ready on this phone');
    }, 1300);
  };

  AD.unloadLocalModel = function (modelId) {
    const model = AD.state.data.agents.find(function (item) { return item.id === modelId; });
    if (!model || model.provider !== 'local' || model.state !== 'ready') return;
    model.state = 'unloaded';
    model.loaded = false;
    if (AD.state.data.settings.defaultAgentId === modelId) {
      const fallback = AD.hostModels()[0];
      if (fallback) AD.state.data.settings.defaultAgentId = fallback.id;
    }
    AD.save();
    AD.render();
    AD.toast(model.name + ' unloaded');
    AD.announce(model.name + ' unloaded');
  };

  AD.importLocalModel = function (modelId) {
    const model = AD.state.data.agents.find(function (item) { return item.id === modelId; });
    if (!model || model.provider !== 'local' || model.state !== 'not_imported') return;
    model.state = 'importing';
    AD.save();
    AD.render();
    setModelTimer(modelId, function () {
      const current = AD.state.data.agents.find(function (item) { return item.id === modelId; });
      if (!current || current.state !== 'importing') return;
      current.state = 'unloaded';
      current.loaded = false;
      AD.save();
      AD.render();
      AD.toast(current.name + ' imported · preview');
      AD.announce(current.name + ' imported');
    }, 1600);
  };

  AD.reconnectHost = function () {
    if (AD.state.ui.connecting) return;
    AD.state.ui.connecting = true;
    AD.render();
    window.setTimeout(function () {
      AD.state.data.host.state = 'connected';
      AD.state.data.host.lastSeen = nowIso();
      AD.state.ui.connecting = false;
      const draft = AD.state.ui.newTask;
      if (draft && draft.errorKind === 'offline') {
        draft.error = '';
        draft.errorKind = '';
      }
      AD.save();
      AD.render();
      AD.toast('Studio Mac connected');
      AD.resumeSimulations();
    }, 900);
  };

  AD.disconnectHost = function () {
    AD.clearTimers();
    AD.state.data.host.state = 'disconnected';
    AD.state.data.host.lastSeen = nowIso();
    AD.state.ui.connecting = false;
    AD.save();
    AD.render();
    AD.toast('Studio Mac disconnected · tasks paused');
  };

  AD.focusComposer = function () {
    window.setTimeout(function () {
      const input = document.getElementById('composer-input');
      if (!input) return;
      input.focus();
      const length = input.value.length;
      input.setSelectionRange(length, length);
      AD.autoGrow(input);
    }, 90);
  };

  AD.copyText = function (text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(text).then(function () { return true; }).catch(function () { return fallbackCopy(text); });
    }
    return Promise.resolve(fallbackCopy(text));
  };

  function fallbackCopy(text) {
    try {
      const area = document.createElement('textarea');
      area.value = text;
      area.style.position = 'fixed';
      area.style.opacity = '0';
      document.body.appendChild(area);
      area.select();
      document.execCommand('copy');
      area.remove();
      return true;
    } catch (error) {
      return false;
    }
  }
})(window.AD);
