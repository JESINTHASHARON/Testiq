
let folderTree = null;
let currentSuite = null;
let currentFolderPath = null;
let currentTests = [];
let allSuiteTests = [];
let collectedChecks = [];
let collectedRequires = [];
let editingTestIndex = null;
let availablePrechecks = {};
let cookieValidator = null;

async function loadCookieValidator() {
  try {
    const res = await fetch("/api/tests/cookie-validator");
    if (!res.ok) {
      cookieValidator = null;
      return;
    }

    const data = await res.json();

    if (data && data.path) {
      cookieValidator = data;
    } else {
      cookieValidator = null;
    }
  } catch (e) {
    cookieValidator = null;
  }
}

async function loadSuites() {
	try {
		const res = await fetch('/api/tests/suites');
		if (!res.ok) throw new Error('Failed to load suites');

		const tree = await res.json();
		folderTree = tree || {};
		const rootFolders = (tree && tree.folders) ? tree.folders : {};

		const suiteList = document.getElementById('suiteList');
		suiteList.innerHTML = "";

		function walkFolder(name, node, parentEl, folderPath, depth) {
			const hasChildren = node.folders && Object.keys(node.folders).length > 0;

			const li = document.createElement('li');
			li.className = 'tree-node';
			li.dataset.path = folderPath;
			li.dataset.parent = folderPath.includes('/')
				? folderPath.substring(0, folderPath.lastIndexOf('/'))
				: "";
			const row = document.createElement('div');
			row.className = 'tree-row';
			row.style.paddingLeft = (depth * 20) + "px";
			row.setAttribute("draggable", "true");
			row.dataset.path = folderPath;
			row.addEventListener("dragstart", onFolderDragStart);
			row.addEventListener("dragover", onFolderDragOver);
			row.addEventListener("drop", onFolderDrop);

			const caret = document.createElement('span');
			caret.className = 'tree-caret';
			row.appendChild(caret);

			const nameSpan = document.createElement('span');
			nameSpan.className = 'tree-name';
			nameSpan.textContent = name;
			row.appendChild(nameSpan);

			const actions = document.createElement('div');
			actions.className = 'suite-item-actions';
			actions.innerHTML = `
				<button class="icon-btn" onclick="event.stopPropagation(); editFolder('${folderPath}')">✎</button>
				<button class="icon-btn" onclick="event.stopPropagation(); deleteFolder('${folderPath}')">🗑</button>
			`;
			row.appendChild(actions);

			li.appendChild(row);
			parentEl.appendChild(li);

			const childrenContainer = document.createElement('ul');
			childrenContainer.className = 'tree-children';
			childrenContainer.style.display = 'none';
			li.appendChild(childrenContainer);

			row.addEventListener('click', (e) => {
				if (e.target.closest('.icon-btn')) return;
				const expanded = childrenContainer.style.display === 'none';
				childrenContainer.style.display = expanded ? 'block' : 'none';
				selectFolder(folderPath);

				document.querySelectorAll('.tree-row').forEach(r => r.classList.remove('active'));
				row.classList.add('active');
			});

			if (hasChildren) {
				Object.entries(node.folders).forEach(([childName, childNode]) => {
					const childPath = `${folderPath}/${childName}`;
					walkFolder(childName, childNode, childrenContainer, childPath, depth + 1);
				});
			}
		}

		Object.entries(rootFolders).forEach(([name, node]) => {
			walkFolder(name, node, suiteList, name, 0);
		});

		const rootNames = Object.keys(rootFolders);
		if (rootNames.length > 0) {
			selectFolder(rootNames[0]);
		}
	} catch (e) {
		console.error(e);
		alert('Error loading suites');
	}
}


async function loadPrechecks() {
    try {
        const res = await fetch('/api/tests/prechecks');
        if (!res.ok) throw new Error('Failed to load prechecks');
        availablePrechecks = await res.json();
    } catch (e) {
        console.error("Precheck load failed", e);
        availablePrechecks = {};
    }
}


function selectFolder(folderPath) {
	currentFolderPath = folderPath;
	currentSuite = folderPath.split('/')[0] || null;
	currentTests = [];
	allSuiteTests = [];
	collectedChecks = [];
	collectedRequires = [];
	editingTestIndex = null;

	document.querySelectorAll('.tree-row').forEach(r => r.classList.remove('active'));
	document.querySelectorAll('.tree-row').forEach(r => {
		if (r.dataset.path === folderPath) {
			r.classList.add('active');
		}
	});


	document.getElementById("currentSuiteTitle").textContent = folderPath;
	loadTestcasesForFolder(folderPath);
}

function selectSuite(suiteName) {
	selectFolder(suiteName);
}

function collectTestsForFolder(tree, folderPath) {
	const result = [];
	if (!tree || !tree.folders) return result;

	const segments = folderPath.split('/');
	let node = { folders: tree.folders };
	for (const seg of segments) {
		if (!node.folders || !node.folders[seg]) {
			return result;
		}
		node = node.folders[seg];
	}

	function walk(n, prefix) {
		const tests = n.tests || [];
		tests.forEach(file => {
			result.push(`${prefix}/${file}`);
		});
		const folders = n.folders || {};
		Object.entries(folders).forEach(([folderName, child]) => {
			walk(child, `${prefix}/${folderName}`);
		});
	}
	walk(node, folderPath);
	return result;
}

function collectTestsForSuite(tree, suiteName) {
	const result = [];
	if (!tree || !tree.folders || !tree.folders[suiteName]) return result;
	const suiteNode = tree.folders[suiteName];

	function walk(node, prefix) {
		const tests = node.tests || [];
		tests.forEach(file => {
			result.push(`${prefix}/${file}`);
		});
		const folders = node.folders || {};
		Object.entries(folders).forEach(([folderName, child]) => {
			walk(child, `${prefix}/${folderName}`);
		});
	}
	walk(suiteNode, suiteName);
	return result;
}

function collectAllTestPaths(tree) {
	const result = [];
	if (!tree) return result;

	function walk(n, prefix) {
		const tests = n.tests || [];
		tests.forEach(file => {
			const p = prefix ? `${prefix}/${file}` : file;
			result.push(p);
		});

		const folders = n.folders || {};
		Object.entries(folders).forEach(([name, child]) => {
			const newPrefix = prefix ? `${prefix}/${name}` : name;
			walk(child, newPrefix);
		});
	}

	walk(tree, "");
	return result;
}

async function loadTestcasesForFolder(folderPath) {
	const container = document.getElementById('testcaseContainer');
	container.innerHTML = `<div class="empty-state">Loading...</div>`;

	if (!folderPath) {
		container.innerHTML = `<div class="empty-state">Select a Suite / Folder</div>`;
		return;
	}

	try {
		const res = await fetch('/api/tests/suites');
		if (!res.ok) throw new Error('Failed to load suite tree');
		const tree = await res.json();
		folderTree = tree || {};

		if (!currentSuite) {
			container.innerHTML = `<div class="empty-state">Select a Suite / Folder</div>`;
			return;
		}

		const suitePaths = collectTestsForSuite(folderTree, currentSuite);
		const allPromises = suitePaths.map(async (p) => {
			const r = await fetch(`/api/tests/test?path=${encodeURIComponent(p)}`);
			if (!r.ok) throw new Error('Failed to read test ' + p);
			const data = await r.json();
			return { path: p, data };
		});
		allSuiteTests = await Promise.all(allPromises);

		const currentPaths = collectTestsForFolder(folderTree, folderPath);
		const pathSet = new Set(currentPaths);
		currentTests = allSuiteTests.filter(t => pathSet.has(t.path));

		if (!currentTests.length) {
			container.innerHTML = `<div class="empty-state">No testcases found</div>`;
			return;
		}

		renderTestcases();
	} catch (e) {
		console.error(e);
		container.innerHTML = `<div class="empty-state">Error loading suite/folder</div>`;
	}
}

async function loadTestcasesForSuite(suiteName) {
	const folderPath =
		(currentFolderPath && currentFolderPath.startsWith(suiteName))
			? currentFolderPath
			: suiteName;
	return loadTestcasesForFolder(folderPath);
}

function normalizeParentIds(parentId) {
	if (parentId === undefined || parentId === null || parentId === "") return [];
	if (Array.isArray(parentId)) {
		return parentId
			.map(v => Number(v))
			.filter(v => !Number.isNaN(v));
	}
	const n = Number(parentId);
	return Number.isNaN(n) ? [] : [n];
}


function renderTestcases() {
	const container = document.getElementById('testcaseContainer');

	if (!currentTests.length) {
		container.innerHTML = `<div class="empty-state">No testcases found</div>`;
		return;
	}

	const html = currentTests.map((item, index) => {
		const tc = item.data;
		const checks = tc.checks || [];
		const method = tc.method || 'GET';
		const endpoint = tc.endpoint || '';
		const parentIds = normalizeParentIds(tc.parentId);

		let parentMetaText = "";
		if (parentIds.length) {
			parentMetaText = `
            <span class="parent-meta">
              Parents: ${parentIds.join(", ")}
            </span>`;
		}
		return `
  <div class="testcase-card"
       data-index="${index}"
       draggable="true"
       ondragstart="onDragStart(event)"
       ondragover="onDragOver(event)"
       ondrop="onDrop(event)">
   
        <div class="testcase-header" data-index="${index}">
          <div class="testcase-title">
            TC-${tc.id ?? "?"}: ${escapeHtml(tc.name || "(no name)")}
          </div>
       <div class="testcase-actions">

         ${(() => {
           const isStarred =
             cookieValidator &&
             cookieValidator.path === item.path;

           return `
             <span
               class="tc-star ${isStarred ? 'active' : ''}"
               title="Use this testcase for Session Validation"
               onclick="event.stopPropagation(); toggleCookieValidator('${item.path}')"
             >★</span>
           `;
         })()}

         <button class="btn btn-small btn-edit" data-action="edit" data-index="${index}">Edit</button>
         <button class="btn btn-small btn-delete" data-action="delete" data-index="${index}">Delete</button>
       </div>


        </div>

        <div id="testcase-body-${index}" style="display:none; margin-top:10px;">


        <div class="testcase-meta">
          <div>
            Endpoint: ${escapeHtml(endpoint)} | Method: ${method}
          </div>

          ${parentIds.length ? parentMetaText : ""}

          ${tc.precheck ? `
            <span class="precheck-meta">
              Precheck: ${escapeHtml(tc.precheck)}
            </span>
          ` : ""}
        </div>


          <div>
            ${renderChecksPreview(checks)}
          </div>
        </div>
      </div>
    `;
	}).join("");

	container.innerHTML = `<div class="testcase-list">${html}</div>`;
	document.querySelectorAll('.testcase-header').forEach(el => {
		el.addEventListener('click', () => {
			const idx = Number(el.dataset.index);
			toggleTestcaseCard(idx);
		});
	});
	document.querySelectorAll('.testcase-actions button').forEach(btn => {
		btn.addEventListener('click', (e) => {
			e.stopPropagation();
			const idx = Number(btn.dataset.index);
			if (btn.dataset.action === "edit") {
				editTestcase(idx);
			} else if (btn.dataset.action === "delete") {
				deleteTestcase(idx);
			}
		});
	});
}

async function toggleCookieValidator(path) {
  try {
    await fetch("/api/tests/cookie-validator", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ path })
    });

    await loadCookieValidator();
    renderTestcases();
  } catch (e) {
    alert("Failed to set cookie validator testcase");
  }
}

let dragSrcIndex = null;

function onDragStart(e) {
	dragSrcIndex = Number(e.currentTarget.dataset.index);
	e.currentTarget.classList.add("dragging");
}
function onDragOver(e) {
	e.preventDefault();
	const el = e.currentTarget;
	document.querySelectorAll(".testcase-card").forEach(c => c.classList.remove("drag-over"));
	el.classList.add("drag-over");
}
async function onDrop(e) {
	e.preventDefault();

	const dragged = document.querySelector(".testcase-card.dragging");
	if (!dragged) return;

	const target = e.currentTarget;
	const list = target.parentElement;

	document.querySelectorAll(".testcase-card")
		.forEach(c => c.classList.remove("drag-over", "dragging"));

	const rect = target.getBoundingClientRect();
	const isAfter = e.clientY > rect.top + rect.height / 2;

	if (isAfter) {
		list.insertBefore(dragged, target.nextSibling);
	} else {
		list.insertBefore(dragged, target);
	}

	const cards = [...list.querySelectorAll(".testcase-card")];
	currentTests = cards.map(c => {
		const oldIndex = Number(c.dataset.index);
		return currentTests[oldIndex];
	});

	renderTestcases();
	dragSrcIndex = null;
	await saveUpdatedOrder();
}

async function saveUpdatedOrder() {
	const folder = currentFolderPath || currentSuite;
	const orderedFiles = currentTests.map(t =>
		t.path.split('/').pop()
	);
	try {
		const res = await fetch(
			`/api/tests/order?folder=${encodeURIComponent(folder)}`, {
			method: 'PUT',
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ order: orderedFiles })
		}
		);
		if (!res.ok) {
			console.error(await res.text());
			alert("Failed to update test order");
		} else {
			console.log("Order updated:", orderedFiles);
		}
	} catch (err) {
		console.error(err);
		alert("Error updating order");
	}
}

let folderDragSrcPath = null;
let folderDragSrcParent = null;

function onFolderDragStart(e) {
	const row = e.currentTarget;
	folderDragSrcPath = row.dataset.path;
	folderDragSrcParent = folderDragSrcPath.includes('/')
		? folderDragSrcPath.substring(0, folderDragSrcPath.lastIndexOf('/'))
		: "";

	row.classList.add('dragging');
}

function onFolderDragOver(e) {
	e.preventDefault();
	e.currentTarget.classList.add('drag-over');
}
async function onFolderDrop(e) {
	e.preventDefault();

	const targetRow = e.currentTarget;
	const draggedRow = document.querySelector('.tree-row.dragging');

	document.querySelectorAll('.tree-row').forEach(r =>
		r.classList.remove("drag-over", "dragging")
	);

	const targetPath = targetRow.dataset.path;
	if (!targetPath || targetPath === folderDragSrcPath) return;

	const targetParent = targetPath.includes('/')
		? targetPath.substring(0, targetPath.lastIndexOf('/'))
		: "";

	if (targetParent !== folderDragSrcParent) {
		alert("Cannot move folder outside its parent!");
		return;
	}

	const rect = targetRow.getBoundingClientRect();
	const isAfter = (e.clientY - rect.top) > (rect.height / 2);
	if (isAfter) {
		targetRow.parentElement.insertBefore(draggedRow, targetRow.nextSibling);
	} else {
		targetRow.parentElement.insertBefore(draggedRow, targetRow);
	}

	await applyFolderOrder(folderDragSrcParent);
}


async function applyFolderOrder(parentFolder) {
	const rows = [...document.querySelectorAll('.tree-row')].filter(r => {
		const p = r.dataset.path;
		const pParent = p.includes('/') ? p.substring(0, p.lastIndexOf('/')) : "";
		return pParent === parentFolder;
	});

	const orderedNames = rows.map(r => r.dataset.path.split('/').pop());
	await saveFolderOrder(parentFolder, orderedNames);
	await loadSuites();
}

async function saveFolderOrder(folder, orderArr) {
	try {
		const res = await fetch(
			`/api/tests/order?folder=${encodeURIComponent(folder)}`, {
			method: 'PUT',
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ order: orderArr })
		}
		);
		if (!res.ok) {
			console.error(await res.text());
			alert("Failed to save folder order");
		}
	} catch (err) {
		console.error(err);
		alert("Error saving folder order");
	}
}

function renderParentCheckboxList(selectedIds = []) {
	const container = document.getElementById('parentListContainer');
	container.innerHTML = "";

	if (!allSuiteTests.length) {
		container.innerHTML = `<div style="color:#a0aec0;">No parents found</div>`;
		return;
	}

	const parentCandidates = [];
	const childParentMap = new Set();

	allSuiteTests.forEach(t => {
		normalizeParentIds(t.data.parentId).forEach(pid => {
			childParentMap.add(pid);
		});
	});

	allSuiteTests.forEach(t => {
		const id = t.data.id;
		if (childParentMap.has(id)) {
			parentCandidates.push(t);
		}
	});

	if (!parentCandidates.length) {
		container.innerHTML = `<div style="color:#a0aec0;">No parents in this suite</div>`;
		return;
	}

	parentCandidates.forEach(t => {
		const id = t.data.id;
		const name = escapeHtml(t.data.name || "");
		const isChecked = selectedIds.includes(id) ? "checked" : "";

		container.innerHTML += `
            <label class="parent-item">
                <input type="checkbox" 
                       name="parentCheckbox"
                       value="${id}"
                       ${isChecked}>
                <span class="parent-item-text">TC-${id}: ${name}</span>
            </label>
        `;
	});

	updateRequiresVisibility();
}


document.addEventListener("change", (e) => {
	if (e.target.name === "parentCheckbox") {
		updateRequiresVisibility();
	}
});


function updateRequiresVisibility() {
	const reqSection = document.getElementById("requiresSection");
	const hasParent = document.querySelectorAll('input[name="parentCheckbox"]:checked').length > 0;

	if (hasParent) {
		reqSection.style.display = "block";
	} else {
		reqSection.style.display = "none";
		collectedRequires = [];
		renderRequiresList();
	}
}


function toggleTestcaseCard(index) {
	const body = document.getElementById(`testcase-body-${index}`);
	if (!body) return;
	const cur = body.style.display;
	body.style.display = (!cur || cur === 'none') ? 'block' : 'none';
}

function renderChecksPreview(checks) {
	if (!checks || !checks.length)
		return `<div style="color:#a0aec0">No checks</div>`;

	return checks.map(c => `
      <div class="check-preview-card">
        <div class="check-type">➣ <strong>${formatCheckTitle(c.type)}</strong></div>
        ${formatCheckLines(c)}
        <br>
      </div>
    `).join("");
}

function openCreateSuiteModal() {
	document.getElementById('suiteModalTitle').textContent = 'Create New Test Suite';
	document.getElementById('suiteForm').reset();
	document.getElementById('suiteModal').classList.add('active');
	document.getElementById('suiteForm').dataset.mode = 'create';
	document.getElementById('suiteForm').dataset.oldName = '';
}

function closeSuiteModal() {
	document.getElementById('suiteModal').classList.remove('active');
	document.getElementById('suiteForm').dataset.mode = '';
	document.getElementById('suiteForm').dataset.oldName = '';
}

document.getElementById('suiteForm').addEventListener('submit', async (e) => {
	e.preventDefault();
	const name = document.getElementById('suiteName').value.trim();
	if (!name) return alert('Suite name required');

	const mode = e.target.dataset.mode || 'create';
	const oldName = e.target.dataset.oldName || '';

	try {
		if (mode === 'edit' && oldName) {
			const res = await fetch(
				`/api/tests/folder/rename?oldPath=${encodeURIComponent(oldName)}&newName=${encodeURIComponent(name)}`,
				{ method: 'PUT' }
			);
			if (!res.ok) throw new Error(await res.text());
			if (currentSuite === oldName) {
				currentSuite = name;
				currentFolderPath = name;
			}
		} else {
			const res = await fetch(`/api/tests/folder?path=${encodeURIComponent(name)}`, {
				method: 'POST'
			});
			if (!res.ok) throw new Error(await res.text());
		}
		closeSuiteModal();
		await loadSuites();
	} catch (err) {
		console.error(err);
		alert('Failed to save suite: ' + err.message);
	}
});

function editFolder(folderPath) {
	const name = folderPath.split('/').pop();
	document.getElementById('suiteModalTitle').textContent = 'Rename Folder';
	document.getElementById('suiteForm').reset();
	document.getElementById('suiteName').value = name;
	document.getElementById('suiteForm').dataset.mode = 'edit';
	document.getElementById('suiteForm').dataset.oldName = folderPath;
	document.getElementById('suiteModal').classList.add('active');
}

async function deleteFolder(folderPath) {
	if (!confirm(`Delete folder "${folderPath}" and ALL testcases under it? This cannot be undone.`))
		return;

	try {
		const res = await fetch(`/api/tests/folder?path=${encodeURIComponent(folderPath)}`, {
			method: 'DELETE'
		});
		if (!res.ok) throw new Error(await res.text());

		await loadSuites();

		if (currentFolderPath && currentFolderPath.startsWith(folderPath)) {
			currentFolderPath = null;
			currentSuite = null;
			document.getElementById("currentSuiteTitle").textContent = "Select a Suite / Folder";
			document.getElementById("testcaseContainer").innerHTML = "";
		}
	} catch (e) {
		alert("Failed to delete folder: " + e.message);
		console.error(e);
	}
}

function editSuite(name) {
	document.getElementById('suiteModalTitle').textContent = 'Edit Test Suite';
	document.getElementById('suiteForm').reset();
	document.getElementById('suiteName').value = name;
	document.getElementById('suiteForm').dataset.mode = 'edit';
	document.getElementById('suiteForm').dataset.oldName = name;
	document.getElementById('suiteModal').classList.add('active');
}

async function deleteSuite(name) {
	if (!confirm(`Delete suite "${name}" and all its folders & testcases?`)) return;
	try {
		const res = await fetch(`/api/tests/folder?path=${encodeURIComponent(name)}`, {
			method: 'DELETE'
		});
		if (!res.ok) throw new Error(await res.text());

		if (currentSuite === name) {
			currentSuite = null;
			currentFolderPath = null;
			currentTests = [];
			allSuiteTests = [];
			document.getElementById("currentSuiteTitle").textContent = "Select a Suite / Folder";
			document.getElementById("testcaseContainer").innerHTML = "";
		}
		await loadSuites();
	} catch (e) {
		console.error(e);
		alert('Failed to delete suite: ' + e.message);
	}
}

function openCreateFolderModal() {
	if (!currentSuite) {
		alert('Select a suite or folder first');
		return;
	}
	document.getElementById('folderForm').reset();
	document.getElementById('folderModal').classList.add('active');
}

function closeFolderModal() {
	document.getElementById('folderModal').classList.remove('active');
}

document.getElementById('folderForm').addEventListener('submit', async (e) => {
	e.preventDefault();
	const name = document.getElementById('folderName').value.trim();
	if (!name) return alert('Folder name required');

	const basePath = currentFolderPath || currentSuite || "";
	const fullPath = basePath ? `${basePath}/${name}` : name;

	try {
		const res = await fetch(`/api/tests/folder?path=${encodeURIComponent(fullPath)}`, {
			method: 'POST'
		});
		if (!res.ok) throw new Error(await res.text());

		closeFolderModal();
		await loadSuites();
		if (basePath) {
			selectFolder(basePath);
		}
	} catch (err) {
		console.error(err);
		alert('Failed to create folder: ' + err.message);
	}
});

function openCreateTestcaseModal() {
	if (!currentSuite || !currentFolderPath) {
		alert('Select a suite/folder first');
		return;
	}

	editingTestIndex = null;
	collectedChecks = [];
	collectedRequires = [];

	document.getElementById('testcaseModalTitle').textContent = 'Create New Test Case';
	document.getElementById('testcaseForm').reset();
	document.getElementById("addedChecksList").innerHTML = "";
	document.getElementById("checkInputContainer").innerHTML = "";
	document.getElementById("checkTypeSelect").value = "";
	document.getElementById("testcaseId").setAttribute("readonly", "true");
	generateNextTestcaseId();
	renderParentCheckboxList([]);
	updateRequiresVisibility();

	renderRequiresList();
	populatePrecheckDropdown("");

	document.getElementById('testcaseModal').classList.add('active');
}

async function generateNextTestcaseId() {
	const idField = document.getElementById("testcaseId");
	try {
		const res = await fetch('/api/tests/suites');
		if (!res.ok) throw new Error('Failed to load suite tree for ID generation');
		const tree = await res.json();
		const paths = collectAllTestPaths(tree);

		if (!paths.length) {
			idField.value = 100;
			return;
		}

		let maxId = 99;
		for (const p of paths) {
			try {
				const r = await fetch(`/api/tests/test?path=${encodeURIComponent(p)}`);
				if (!r.ok) continue;
				const data = await r.json();
				const id = Number(data.id || 0);
				if (id > maxId) maxId = id;
			} catch (e) {

			}
		}
		idField.value = maxId + 1;
	} catch (e) {
		console.error(e);
		idField.value = "";
	}
}

function closeTestcaseModal() {
	document.getElementById('testcaseModal').classList.remove('active');
	editingTestIndex = null;
}

function populateParentDropdown(selectedIdsArray) {
	const sel = document.getElementById('testcaseParent');
	sel.innerHTML = "";
	const roots = (allSuiteTests || []).filter(t => {
		const pids = normalizeParentIds(t.data.parentId);
		return pids.length === 0;
	});

	if (!roots.length) {
		const opt = document.createElement('option');
		opt.textContent = "No available parent tests (root-level)";
		opt.value = "";
		sel.appendChild(opt);
		return;
	}

	roots.forEach(t => {
		const id = t.data.id;
		if (id == null) return;
		const name = t.data.name || '';
		const opt = document.createElement('option');
		opt.value = String(id);
		opt.textContent = `TC-${id}: ${name}`;
		sel.appendChild(opt);
	});

	const selectedSet = new Set((selectedIdsArray || []).map(v => String(v)));
	Array.from(sel.options).forEach(o => {
		if (selectedSet.has(o.value)) {
			o.selected = true;
		}
	});
}

function normalizeRequires(req) {
	if (!Array.isArray(req)) return [];
	const result = [];
	req.forEach(r => {
		if (r && typeof r === "object") {
			const name = r.name != null ? String(r.name) : null;
			const path = r.path != null ? String(r.path) : "";
			if (name) {
				result.push({ name, path });
			}
		} else if (typeof r === "string") {
			result.push({ name: r, path: r });
		}
	});
	return result;
}
function renderRequiresList() {
	const list = document.getElementById('requiresList');
	if (!collectedRequires.length) {
		list.innerHTML = `<div style="color:#a0aec0;font-size:0.85rem;">No requires defined</div>`;
		return;
	}

	list.innerHTML = collectedRequires.map((r, i) => `
		<div class="require-card">
			<div>
			   <div class="require-name">${escapeHtml(r.name)}</div>
			   <div class="require-path">Path: ${escapeHtml(r.path || '')}</div>
			</div>
			<button onclick="removeRequireEntry(${i})">Remove</button>
		</div>
	`).join("");
}

function addRequireEntry() {
	const name = document.getElementById('requireName').value.trim();
	const path = document.getElementById('requirePath').value.trim();

	if (!name) {
		alert("Require name is required");
		return;
	}

	collectedRequires.push({ name, path });
	document.getElementById('requireName').value = "";
	document.getElementById('requirePath').value = "";
	renderRequiresList();
}

function removeRequireEntry(index) {
	if (index < 0 || index >= collectedRequires.length) return;
	collectedRequires.splice(index, 1);
	renderRequiresList();
}

function editTestcase(index) {
	if (index == null || index < 0 || index >= currentTests.length) return;

	const tc = currentTests[index].data;

	editingTestIndex = index;
	collectedChecks = Array.isArray(tc.checks)
		? JSON.parse(JSON.stringify(tc.checks))
		: [];

	collectedRequires = normalizeRequires(tc.requires);

	document.getElementById('testcaseModalTitle').textContent = 'Edit Test Case';
	document.getElementById('testcaseForm').reset();

	document.getElementById("testcaseId").value = tc.id ?? '';
	document.getElementById("testcaseId").setAttribute("readonly", "true");
	document.getElementById('testcaseName').value = tc.name || '';
	document.getElementById('testcaseEndpoint').value = tc.endpoint || '';
	document.getElementById('testcaseMethod').value = tc.method || 'GET';
	document.getElementById('testcaseStatus').value = tc.expectedStatus ?? 200;

	const parentIds = normalizeParentIds(tc.parentId);
	renderParentCheckboxList(parentIds);
	updateRequiresVisibility();

	renderAddedChecks();
	renderRequiresList();
	populatePrecheckDropdown(tc.precheck || "");

	document.getElementById('testcaseModal').classList.add('active');
}

function collectDescendants(rootId) {
	const result = [];
	const visited = new Set();

	function dfs(id) {
		allSuiteTests.forEach(t => {
			const tid = t.data.id;
			if (tid == null) return;
			const pids = normalizeParentIds(t.data.parentId);
			if (pids.includes(id) && !visited.has(tid)) {
				visited.add(tid);
				result.push(t);
				dfs(Number(tid));
			}
		});
	}

	dfs(rootId);
	return result;
}

async function cleanupParentsAfterChildDelete(childData) {
	const parentIds = normalizeParentIds(childData.parentId);
	if (!parentIds.length) return;

	const childReqs = normalizeRequires(childData.requires);
	const childNames = new Set(childReqs.map(r => r.name));

	for (const pid of parentIds) {
		const parent = allSuiteTests.find(t => Number(t.data.id) === Number(pid));
		if (!parent) continue;

		try {
			const getRes = await fetch(`/api/tests/test?path=${encodeURIComponent(parent.path)}`);
			if (!getRes.ok) continue;
			const parentData = await getRes.json();

			const existing = normalizeRequires(parentData.requires);
			const filtered = existing.filter(r => !childNames.has(r.name));

			parentData.requires = filtered;

			await fetch(`/api/tests/test?path=${encodeURIComponent(parent.path)}`, {
				method: 'PUT',
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(parentData)
			});
		} catch (e) {
			console.error("cleanupParentsAfterChildDelete error:", e);
		}
	}
}

async function deleteTestcase(index) {
	if (index == null || index < 0 || index >= currentTests.length) return;

	const item = currentTests[index];
	const tc = item.data;
	const rootPath = item.path;
	const rootId = Number(tc.id);

	const descendants = (rootId != null && !Number.isNaN(rootId))
		? collectDescendants(rootId)
		: [];

	const descSummary = descendants.length
		? "\n\nThis will also delete these child testcases:\n" +
		descendants.map(d => `TC-${d.data.id}: ${d.data.name}`).join("\n")
		: "";

	let msg = `Delete testcase TC-${tc.id}: "${tc.name}"?${descSummary}\n\nThis action cannot be undone.`;
	if (!confirm(msg)) return;

	try {
		const toDelete = [{ path: rootPath, data: tc }];
		const seenPaths = new Set([rootPath]);
		descendants.forEach(d => {
			if (!seenPaths.has(d.path)) {
				seenPaths.add(d.path);
				toDelete.push({ path: d.path, data: d.data });
			}
		});

		for (const t of toDelete) {
			const res = await fetch(`/api/tests/test?path=${encodeURIComponent(t.path)}`, {
				method: 'DELETE'
			});
			if (!res.ok) {
				console.error("Failed to delete", t.path, await res.text());
			} else {
				await cleanupParentsAfterChildDelete(t.data);
			}
		}
		await loadTestcasesForFolder(currentFolderPath || currentSuite);
	} catch (e) {
		console.error(e);
		alert('Delete failed: ' + e.message);
	}
}

window.addEventListener('click', (e) => {
	if (e.target.classList.contains('modal')) {
		e.target.classList.remove('active');
	}
});

function renderCheckInputs() {
	const type = document.getElementById("checkTypeSelect").value;
	const box = document.getElementById("checkInputContainer");
	if (!type) { box.innerHTML = ""; return; }

	if (type === "patternMatch") {
		box.innerHTML = `
		  <div style="display:flex; align-items:center; gap:6px; margin-bottom:8px;">
		    <input id="chk_path" placeholder="Path (eg: user.id)" style="flex:1;">
		    <span class="path-help-icon" onclick="openHelpModal()">?</span>
		  </div>
		  <div>
		    <input id="chk_pattern" placeholder="Regex Pattern (eg: ^[0-9]+$)">
		  </div>
		`;
	} else if (type === "keyValue") {
		box.innerHTML = `
		  <div style="display:flex; align-items:center; gap:6px; margin-bottom:8px;">
		    <input id="chk_path" placeholder="Path (eg: user.id)" style="flex:1;">
		    <span class="path-help-icon" onclick="openHelpModal()">?</span>
		  </div>
		`;
	} else if (type === "fieldExistence") {
		box.innerHTML = `
		  <div style="display:flex; align-items:center; gap:6px; margin-bottom:8px;">
		    <input id="chk_path" placeholder="Path (eg: user.id)" style="flex:1;">
		    <span class="path-help-icon" onclick="openHelpModal()">?</span>
		  </div>
	      <input id="chk_fields" placeholder="Fields (comma separated)" style="margin-bottom:8px;">
	      <input id="chk_fieldAny" placeholder="FieldAny (comma separated) optional" style="margin-bottom:8px;">
	    `;
	} else if (type === "valueMatch") {
		box.innerHTML = `
      <div style="margin-bottom:8px;">
         <label class="small-label">JSON Path</label>
         <input id="chk_path" placeholder="e.g. user.age" style="width:100%;">
      </div>

      <div style="margin-bottom:8px;">
         <label class="small-label">Operator</label>
         <select id="chk_operator" style="width:100%;">
            <option value="==">Equals</option>
            <option value="!=">Not Equals</option>
            <option value=">">Greater Than</option>
            <option value="<">Less Than</option>
            <option value=">=">Greater Or Equal</option>
            <option value="<=">Less Or Equal</option>
            <option value="CONTAINS">Contains</option>
            <option value="NOTCONTAINS">Not Contains</option>
            <option value="STARTSWITH">Starts With</option>
            <option value="ENDSWITH">Ends With</option>
            <option value="EMPTY">Empty</option>
            <option value="NOTEMPTY">Not Empty</option>
         </select>
      </div>

      <div style="margin-bottom:8px;">
         <label class="small-label">Logic (for multiple expected)</label>
         <select id="chk_logic" style="width:100%;">
            <option value="AND">All must match (AND)</option>
            <option value="OR">Any matching is enough (OR)</option>
         </select>
      </div>

      <div style="margin-bottom:8px;">
         <label class="small-label">Expected Type</label>
         <select id="chk_expectedType" style="width:100%;">
            <option value="">Any</option>
            <option value="string">String</option>
            <option value="number">Number</option>
            <option value="boolean">Boolean</option>
            <option value="array">Array</option>
            <option value="object">Object</option>
            <option value="null">Null</option>
         </select>
      </div>

      <div>
         <label class="small-label">Expected Value(s)</label>
         <input id="chk_expected" placeholder='Single OR Multiple (comma separated)' style="width:100%;">
      </div>
    `;
	}
}

function addCheck() {
	const type = document.getElementById("checkTypeSelect").value;
	if (!type) return alert("Select a check type");
	const errorBox = document.getElementById("testcaseErrorMsg");
	if (errorBox) {
		errorBox.style.display = "none";
		errorBox.textContent = "";
	}
	const getVal = id => {
		const el = document.getElementById(id);
		return el ? el.value.trim() : "";
	};

	let check;
	if (type === "patternMatch") {
		check = { type, path: getVal("chk_path"), pattern: getVal("chk_pattern") };
	} else if (type === "keyValue") {
		check = { type, path: getVal("chk_path") };
	} else if (type === "fieldExistence") {
		const fields = getVal("chk_fields");
		const fieldAny = getVal("chk_fieldAny");

		check = {
			type,
			path: getVal("chk_path"),
			fields: fields ? fields.split(",").map(s => s.trim()) : []
		};

		if (fieldAny) {
			check.fieldAny = fieldAny.split(",").map(s => s.trim());
		}
	} else if (type === "valueMatch") {
		const path = getVal("chk_path");
		const operator = document.getElementById("chk_operator").value || "==";
		const logic = document.getElementById("chk_logic").value || "AND";
		const expectedType = document.getElementById("chk_expectedType").value || null;
		const expectedRaw = getVal("chk_expected");

		let expected = null;
		if (expectedRaw) {
			const list = expectedRaw.split(",").map(s => s.trim());
			expected = list.length > 1 ? list : list[0];
		}

		check = {
			type,
			path,
			operator,
			logic,
			expectedType,
			expected
		};
	}
	else {
		return alert("Unsupported check type");
	}

	if (!check.path && type !== "fieldExistence") {
		if (!confirm("Path is empty. Continue?")) return;
	}

	collectedChecks.push(check);
	renderAddedChecks();

	document.getElementById("checkInputContainer").innerHTML = "";
	document.getElementById("checkTypeSelect").value = "";
}

function renderAddedChecks() {
	const list = document.getElementById("addedChecksList");

	if (!collectedChecks || !collectedChecks.length) {
		list.innerHTML = `<div style="color:#a0aec0">No checks added</div>`;
		return;
	}

	list.innerHTML = collectedChecks
		.map((c, i) => `
	      <div class="check-builder-card">
	        <div class="check-builder-header">
	          <strong>${formatCheckTitle(c.type)}</strong>
	          <button class="btn btn-small btn-delete" onclick="removeCheck(${i})">Remove</button>
	        </div>
	        <div class="check-builder-body">
	          ${formatCheckLines(c)}
	        </div>
	      </div>
	    `)
		.join("");
}

function formatCheckTitle(type) {
	switch (type) {
		case "patternMatch": return "Pattern Match";
		case "keyValue": return "Key Presence Validation";
		case "fieldExistence": return "Field Existence Check";
		case "valueMatch": return "Value Match";
		default: return type;
	}
}

function infoIcon(path) {
	let structure = "";

	if (path) {
		try {
			structure = structureFromPath(path);
		} catch (e) {
			structure = "Unable to generate structure.";
		}
	}

	return `
    <div class="info-icon-container" style="display:inline-block; margin-left: 6px;">
      <span class="info-icon">i</span>
      <div class="info-popup">
        <strong>Structure at this path:</strong>
        <pre style="
          background:#1a202c;
          color:#e2e8f0;
          padding:8px;
          border-radius:6px;
          white-space:pre-wrap;
          margin-top:6px;
          font-size:0.75rem;
          max-height:200px;
          overflow:auto;
        ">${structure}</pre>
      </div>
    </div>
  `;
}

function formatCheckLines(check) {
	let html = ``;

	if (check.path) {
		html += `
		  <div>
		    Path: ${check.path}
		    ${infoIcon(check.path)}
		  </div>
		`;
	}

	if (check.pattern) {
		html += `<div>Pattern: ${check.pattern}</div>`;
	}

	if (check.fields) {
		html += `<div>Required Fields: ${check.fields.join(", ")}</div>`;
	}

	if (check.fieldAny) {
		html += `<div>Any Field Match: ${check.fieldAny.join(", ")}</div>`;
	}
	if (check.operator) {
		html += `<div>Operator: ${check.operator}</div>`;
	}

	if (check.logic) {
		html += `<div>Logic: ${check.logic}</div>`;
	}

	if (check.expectedType) {
		html += `<div>Expected Type: ${check.expectedType}</div>`;
	}

	if (check.expected !== undefined) {
		html += `<div>Expected: ${Array.isArray(check.expected)
			? check.expected.join(", ")
			: check.expected
			}</div>`;
	}

	return `<div class="check-lines">${html}</div>`;
}

function removeCheck(i) {
	collectedChecks.splice(i, 1);
	renderAddedChecks();
}

document.getElementById('testcaseForm').addEventListener('submit', async (e) => {
	e.preventDefault();

	const errorBox = document.getElementById("testcaseErrorMsg");
	errorBox.style.display = "none";
	errorBox.textContent = "";

	if (!currentSuite || !currentFolderPath)
		return showError("Please select a Suite or Folder first.");

	const idValRaw = document.getElementById('testcaseId').value;
	const idVal = idValRaw ? Number(idValRaw) : undefined;

	const name = document.getElementById('testcaseName').value.trim();
	const endpoint = document.getElementById('testcaseEndpoint').value.trim();
	const method = document.getElementById('testcaseMethod').value;
	const expectedStatus = Number(document.getElementById('testcaseStatus').value);
    const precheck = document.getElementById("testcasePrecheck").value || null;


	if (!name) return showError("Testcase Name is required.");
	if (!endpoint) return showError("Endpoint is required.");

	if (!collectedChecks || collectedChecks.length === 0) {
		return showError("Add atleast one check to save testcase");
	}

	const selectedParentIds = [];
	document.querySelectorAll('input[name="parentCheckbox"]:checked').forEach(ch => {
		const id = Number(ch.value);
		if (!Number.isNaN(id)) selectedParentIds.push(id);
	});
	let jsonPathForFolder;
	if (editingTestIndex != null) {
		const filePath = currentTests[editingTestIndex].path || '';
		jsonPathForFolder = filePath.includes('/')
			? filePath.substring(0, filePath.lastIndexOf('/'))
			: currentFolderPath || currentSuite;
	} else {
		jsonPathForFolder = currentFolderPath || currentSuite;
	}

	let parentIdValue = null;
	if (selectedParentIds.length === 1)
		parentIdValue = selectedParentIds[0];
	else if (selectedParentIds.length > 1)
		parentIdValue = selectedParentIds;

	let tc;
	if (editingTestIndex != null) {
		const existing = JSON.parse(JSON.stringify(currentTests[editingTestIndex].data));
		if (idVal !== undefined) existing.id = idVal;
		existing.name = name;
		existing.endpoint = endpoint;
		existing.method = method;
		existing.expectedStatus = expectedStatus;
		existing.suite = currentSuite;
		existing.path = jsonPathForFolder;
		existing.checks = collectedChecks;
		existing.requires = collectedRequires;
		existing.parentId = parentIdValue;
		existing.precheck = precheck;

		tc = existing;
	} else {
        tc = {
            name, endpoint, method, expectedStatus,
            suite: currentSuite,
            path: jsonPathForFolder,
            checks: collectedChecks,
            requires: collectedRequires,
            parentId: parentIdValue,
            precheck: precheck
        };

		if (idVal !== undefined) tc.id = idVal;
	}

	try {
		let path, methodHttp;

		if (editingTestIndex != null) {
			path = currentTests[editingTestIndex].path;
			methodHttp = 'PUT';
		} else {
			const safeName = name.replace(/[\\/]/g, "_");
			const fileName = `${safeName}.json`;
			const folderPath = currentFolderPath || currentSuite;
			path = `${folderPath}/${fileName}`;
			methodHttp = 'POST';
		}

		const res = await fetch(`/api/tests/test?path=${encodeURIComponent(path)}`, {
			method: methodHttp,
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(tc)
		});

		if (!res.ok) {
			const text = await res.text();
			return showError(text || "Failed to save testcase.");
		}

		closeTestcaseModal();
		await loadTestcasesForFolder(currentFolderPath || currentSuite);

	} catch (err) {
		return showError(err.message || "Unexpected error occurred.");
	}
});

function showError(msg) {
	const errorBox = document.getElementById("testcaseErrorMsg");
	if (!errorBox) return;

	errorBox.textContent = msg;
	errorBox.style.display = "block";
}

function openHelpModal() {
	document.getElementById("helpModal").classList.add("active");
}

function closeHelpModal() {
	document.getElementById("helpModal").classList.remove("active");
}
function structureFromPath(path) {
	if (!path || path === "$") return "Root JSON Object";

	path = path.replace(/^\$\./, "").replace(/^\$/, "");
	let segments = path.split(".");
	let root = {};
	let current = root;

	const lastSeg = segments[segments.length - 1];
	const endsOnValue = !lastSeg.includes("[");

	if (segments[0].match(/^\[\d+\]$/)) {
		let idx = parseInt(segments[0].slice(1, -1));
		root = [];
		root[idx] = {};
		current = root[idx];
		segments.shift();
	}

	else if (segments[0].startsWith("[") && segments[0].endsWith("]")) {
		let [k, v] = segments.shift().slice(1, -1).split("=");
		root = [{ [k.trim()]: v.trim() }];
		current = root[0];
	}

	segments.forEach((seg, index) => {
		const isLast = index === segments.length - 1;

		if (seg.includes("[")) {
			let field = seg.substring(0, seg.indexOf("["));
			let inside = seg.substring(seg.indexOf("[") + 1, seg.indexOf("]"));

			if (!current[field]) current[field] = [];

			if (inside.includes("=")) {
				let [fk, fv] = inside.split("=");
				let obj = { [fk.trim()]: fv.trim() };
				current[field].push(obj);

				if (isLast) obj["_comment"] = "// path stops here";

				current = obj;
			}

			else if (/^\d+$/.test(inside)) {
				let idx = parseInt(inside);
				current[field][idx] = current[field][idx] || {};
				current = current[field][idx];

				if (isLast) current["_comment"] = "// path stops here";
			}

			else if (inside === "*") {
				let obj = {};
				current[field].push(obj);
				current = obj;

				if (isLast) obj["_comment"] = "// path stops here";
			}
		}

		else {
			if (!current[seg]) {
				current[seg] = isLast && endsOnValue ? "<target>" : {};
			}
			current = current[seg];

			if (isLast && !endsOnValue) {
				current["_comment"] = "// path stops here";
			}
		}
	});

	return formatJS(root, 0);
}
function formatJS(obj, indent = 0) {
	const pad = "  ".repeat(indent);

	if (Array.isArray(obj)) {
		let out = "[\n";
		obj.forEach(item => {
			out += pad + "  " + formatJS(item, indent + 1) + ",\n";
		});
		return out + pad + "]";
	}

	if (typeof obj === "string") {
		return `"${obj}"`;
	}

	let out = "{\n";

	for (let key in obj) {
		if (key === "_comment") {
			out += pad + "  " + obj[key] + "\n";
			continue;
		}

		out += pad + "  " + `"${key}": ` + formatJS(obj[key], indent + 1) + ",\n";
	}

	return out + pad + "}";
}

function escapeHtml(str) {
	return String(str)
		.replace(/&/g, "&amp;")
		.replace(/</g, "&lt;")
		.replace(/>/g, "&gt;")
		.replace(/"/g, "&quot;");
}

const AUTO_SCROLL_SPEED = 27;
const AUTO_SCROLL_MARGIN = 80;

document.addEventListener("dragover", function(e) {
	const viewportHeight = window.innerHeight;
	const y = e.clientY;

	if (y < AUTO_SCROLL_MARGIN) {
		window.scrollBy(0, -AUTO_SCROLL_SPEED);
	}

	if (y > viewportHeight - AUTO_SCROLL_MARGIN) {
		window.scrollBy(0, AUTO_SCROLL_SPEED);
	}
});
function populatePrecheckDropdown(selected = "") {
    const sel = document.getElementById("testcasePrecheck");
    if (!sel) return;

    sel.innerHTML = `<option value="">None</option>`;

    Object.keys(availablePrechecks).forEach(name => {
        const opt = document.createElement("option");
        opt.value = name;
        opt.textContent = name;
        if (name === selected) opt.selected = true;
        sel.appendChild(opt);
    });
}


let editingPrecheckName = null;

function openPrecheckManager() {
  renderPrecheckList();
  createNewPrecheck();
  document.getElementById("precheckModal").classList.add("active");
}

function closePrecheckModal() {
  document.getElementById("precheckModal").classList.remove("active");
  editingPrecheckName = null;
}
function renderPrecheckList() {
  const ul = document.getElementById("precheckList");
  ul.innerHTML = "";

  Object.keys(availablePrechecks).forEach(name => {
    const li = document.createElement("li");
    li.style.cursor = "pointer";
    li.textContent = name;
    li.onclick = () => editPrecheck(name);
    ul.appendChild(li);
  });
}

async function deletePrecheck() {
  if (!editingPrecheckName) return;

  if (!confirm(`Delete precheck "${editingPrecheckName}"?`)) return;

  await fetch(`/api/tests/prechecks/${encodeURIComponent(editingPrecheckName)}`, {
    method: "DELETE"
  });

  editingPrecheckName = null;
  await loadPrechecks();
  renderPrecheckList();
  createNewPrecheck();
  populatePrecheckDropdown();
}

let collectedPrecheckConditions = [];

function renderPrecheckConditions() {
  const mode = document.getElementById("pc_mode").value;
  document.getElementById("pc_single_block").style.display =
    mode === "single" ? "block" : "none";
  document.getElementById("pc_multi_block").style.display =
    mode === "multi" ? "block" : "none";
}

function addPrecheckCondition() {
  collectedPrecheckConditions.push({ path: "", operator: "==", value: "" });
  renderPrecheckConditionRows();
}

function renderPrecheckConditionRows() {
  const box = document.getElementById("pc_conditions");
  box.innerHTML = "";

  collectedPrecheckConditions.forEach((c, i) => {
    box.innerHTML += `
      <div style="display:flex; gap:6px; margin-bottom:6px;">
        <input placeholder="Path" value="${c.path}"
          onchange="collectedPrecheckConditions[${i}].path=this.value">
        <select onchange="collectedPrecheckConditions[${i}].operator=this.value">
          <option value="==" ${c.operator=="=="?"selected":""}>==</option>
          <option value="!=" ${c.operator=="!="?"selected":""}>!=</option>
          <option value=">" ${c.operator==">"?"selected":""}>&gt;</option>
          <option value="<" ${c.operator=="<"?"selected":""}>&lt;</option>
          <option value="contains" ${c.operator=="contains"?"selected":""}>contains</option>
        </select>
        <input placeholder="Value" value="${c.value}"
          onchange="collectedPrecheckConditions[${i}].value=this.value">
        <button onclick="removePrecheckCondition(${i})">✕</button>
      </div>
    `;
  });
}

function removePrecheckCondition(i) {
  collectedPrecheckConditions.splice(i, 1);
  renderPrecheckConditionRows();
}

function createNewPrecheck() {
  editingPrecheckName = null;
  collectedPrecheckConditions = [];
  document.getElementById("pc_name").value = "";
  document.getElementById("pc_endpoint").value = "";
  document.getElementById("pc_method").value = "GET";
  document.getElementById("pc_mode").value = "single";
  renderPrecheckConditions();
}

function editPrecheck(name) {
  const rule = availablePrechecks[name];
  editingPrecheckName = name;

  document.getElementById("pc_name").value = name;
  document.getElementById("pc_name").disabled = true;
  document.getElementById("pc_endpoint").value = rule.endpoint;
  document.getElementById("pc_method").value = rule.method || "GET";

  if (rule.conditions) {
    document.getElementById("pc_mode").value = "multi";
    document.getElementById("pc_logic").value = rule.logic || "AND";
    collectedPrecheckConditions = JSON.parse(JSON.stringify(rule.conditions));
    renderPrecheckConditionRows();
  } else {
    document.getElementById("pc_mode").value = "single";
    document.getElementById("pc_path").value = rule.extractPath;
    document.getElementById("pc_operator").value = rule.operator;
    document.getElementById("pc_value").value = rule.value;
  }

  renderPrecheckConditions();
}

async function savePrecheck() {
  const name = document.getElementById("pc_name").value.trim();
  if (!name) return alert("Precheck name required");

  const mode = document.getElementById("pc_mode").value;
  let rule;

  if (mode === "multi") {
    rule = {
      endpoint: document.getElementById("pc_endpoint").value,
      method: document.getElementById("pc_method").value,
      logic: document.getElementById("pc_logic").value,
      conditions: collectedPrecheckConditions
    };
  } else {
    rule = {
      endpoint: document.getElementById("pc_endpoint").value,
      method: document.getElementById("pc_method").value,
      extractPath: document.getElementById("pc_path").value,
      operator: document.getElementById("pc_operator").value,
      value: document.getElementById("pc_value").value
    };
  }

  const url = editingPrecheckName
    ? `/api/tests/prechecks/${encodeURIComponent(name)}`
    : `/api/tests/prechecks`;

  const method = editingPrecheckName ? "PUT" : "POST";
  const body = editingPrecheckName ? rule : { name, rule };

  await fetch(url, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });

  await loadPrechecks();
  renderPrecheckList();
  populatePrecheckDropdown();
}

(async () => {
  await loadCookieValidator();
  await loadPrechecks();
  await loadSuites();
})();
