const IS_RESULTS_PAGE = window.location.pathname.includes("result.html");
const IS_EXECUTE_PAGE =
	window.location.pathname.includes("execute.html") ||
	window.location.pathname.includes("index.html");

const availableSidebar = document.getElementById("availableSidebarScroll");
const selectedSidebar = document.getElementById("selectedSidebarScroll");
const uploadForm = document.getElementById("uploadForm");
const resultsContainer = document.getElementById("results-container");
const resultsHeader = document.getElementById("results-header");
const summaryStats = document.getElementById("summary-stats");
const sidePanel = document.getElementById("sidePanel");
const sidePanelOverlay = document.getElementById("sidePanelOverlay");
const sidePanelClose = document.getElementById("sidePanelClose");
const sidePanelTitle = document.getElementById("sidePanelTitle");
const sidePanelBody = document.getElementById("sidePanelBody");
const verifyBtn = document.getElementById("verifyCookiesBtn");
const runBtn = document.getElementById("runTestsBtn");
const cookieValidationResult = document.getElementById("cookieValidationResult");
const cookieCounterBadge = document.getElementById("cookieCounterBadge");
const goToRunTestsBtn = document.getElementById("goToRunTests");

const jsonModal = document.getElementById("jsonModal");
const jsonModalTitle = document.getElementById("jsonModalTitle");
const jsonModalBody = document.getElementById("jsonModalBody");
const closeJsonModal = document.getElementById("closeJsonModal");

const csvHelpIcon = document.getElementById("csvHelpIcon");
const csvHelpTooltip = document.getElementById("csvHelpTooltip");
const downloadSampleCsv = document.getElementById("downloadSampleCsv");

const safe = id => document.getElementById(id);

let testFiles = {};
let selectedTests = [];
let backendOutput = null;
let parentMap = {};

let currentChart = null;
let cleanedCsvRows = [];
let cleanedCsvBlob = null;
let cookieNamesMap = {};
let totalUploadedCookies = 0;
let totalValidCookies = 0;


if (verifyBtn) verifyBtn.style.display = "none";

if (csvHelpIcon && csvHelpTooltip) {
	csvHelpIcon.addEventListener("mouseover", (e) => {
		e.stopPropagation();
		csvHelpTooltip.style.display =
			csvHelpTooltip.style.display === "block" ? "none" : "block";
	});

	window.addEventListener("click", () => {
		csvHelpTooltip.style.display = "none";
	});
}

let loaderInterval = null;
let loaderPercent = 0;

function startProgressAnimation() {
	const bar = document.getElementById("progressBarFill");
	const pct = document.getElementById("progressPercent");
	if (!bar || !pct) return;

	loaderPercent = 0;

	loaderInterval = setInterval(() => {
		if (loaderPercent < 95) {
			loaderPercent += Math.random() * 3.2;
			if (loaderPercent > 95) loaderPercent = 95;
		}
		bar.style.width = loaderPercent + "%";
		pct.textContent = Math.floor(loaderPercent) + "%";
	}, 150);
}

async function finishProgressAnimation() {
	const bar = document.getElementById("progressBarFill");
	const pct = document.getElementById("progressPercent");
	if (!bar || !pct) return;

	clearInterval(loaderInterval);

	loaderPercent = 100;
	bar.style.width = "100%";
	pct.textContent = "100%";

	return new Promise(resolve => setTimeout(resolve, 250));
}

async function loadDynamicSuites() {
	try {
		const res = await fetch("http://localhost:8080/api/tests/suites");
		if (!res.ok) throw new Error("Failed to load suites");
		testFiles = await res.json();
		renderAvailableTests();
	} catch (err) {
		console.warn("loadDynamicSuites:", err);
		if (availableSidebar) {
			availableSidebar.innerHTML = `
        <h3>Available TestSuites</h3>
        <div style="color:#ef4444">Failed to load suites</div>`;
		}
	}
}

if (verifyBtn) {
	verifyBtn.addEventListener("click", async () => {
		const fileInput = document.getElementById("csvFile");
		const file = fileInput?.files?.[0];


		if (!file) {
			alert("Please upload a CSV first.");
			return;
		}

		verifyBtn.style.display = "none";

		if (!cookieValidationResult) return;
		cookieValidationResult.style.display = "block";
		cookieValidationResult.innerHTML = `
      <div class="loading2" style="font-size: 8px; color: #2563eb; padding: 20px;">
        Validating your CSV...
      </div>
    `;

		Papa.parse(file, {
			header: true,
			skipEmptyLines: false,
			complete: async function(result) {
				let rows = result.data;
				let valid = 0;
				let invalid = 0;

				cleanedCsvRows = [];

				for (const r of rows) {


				    const hasAnyValue = Object.values(r).some(
                        v => v && v.toString().trim() !== ""
                    );
                    if (!hasAnyValue) continue;

                    let cookieName = "";
                    let baseUrl = "";
                    let cookie = "";
                    let headers = {};

                    Object.keys(r).forEach(col => {
                        const value = (r[col] || "").trim();
                        if (!value) return;

                        const key = col.trim().toLowerCase();

                        if (key === "account name") {
                            cookieName = value;
                        } else if (key === "base url") {
                            baseUrl = value;
                        } else if (key === "cookie") {
                            cookie = value;
                        } else {
                            headers[col.trim()] = value;
                        }
                    });

                    if (!cookieName || !baseUrl || !cookie) {
                        invalid++;
                        continue;
                    }

                    const resp = await fetch("http://localhost:8080/api/validateCookie", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({
                            cookieName,
                            baseUrl,
                            cookie,
                            headers
                        })
                    });


					const data = await resp.json();
					if (!data.isValid) {
						invalid++;
						continue;
					}
                    cleanedCsvRows.push({
                        cookieName,
                        baseUrl,
                        cookie,
                        ...headers
                    });

					valid++;
				}

				totalUploadedCookies = valid + invalid;
				totalValidCookies = valid;

				if (valid > 0) {
					const csvString = Papa.unparse(cleanedCsvRows);
					cleanedCsvBlob = new Blob([csvString], { type: "text/csv" });
				} else {
					cleanedCsvBlob = null;
				}

				cookieValidationResult.style.display = "block";
				cookieValidationResult.innerHTML = `
                  <div>
                    <span class="valid-count">${valid}</span> valid rows
                    <span class="invalid-count">${invalid}</span> invalid rows
                  </div>
                  <div class="validation-actions">
                    <button id="cookieProceedInline" class="action-btn proceed-btn">Proceed</button>
                    <button id="cookieCancelInline" class="action-btn cancel-btn">Cancel</button>
                  </div>
                `;

				document
					.getElementById("cookieProceedInline")
					?.addEventListener("click", handleCookieProceed);

				document
					.getElementById("cookieCancelInline")
					?.addEventListener("click", () => {
						cookieValidationResult.innerHTML = "";
						cookieValidationResult.style.display = "none";
						verifyBtn.style.display = "inline-block";
						cleanedCsvRows = [];
						cleanedCsvBlob = null;
						verifyBtn.style.display = "none";

						const csvInput = document.getElementById("csvFile");
						if (csvInput) csvInput.value = "";
					});
			}
		});
	});
}

if (goToRunTestsBtn) {
	goToRunTestsBtn.addEventListener("click", async () => {
		if (selectedTests.length === 0) {
			alert("Please select at least one TestSuite to continue.");
			return;
		}

		const page1 = document.getElementById("page1");
		const page2 = document.getElementById("page2");
		const page3 = document.getElementById("page3");
		const avail = document.getElementById("available-sidebar");
		const selected = document.getElementById("selected-sidebar");
		const loader = document.getElementById("progressLoader");

		if (page1) page1.style.display = "none";
		if (page2) page2.style.display = "none";
		if (avail) avail.style.display = "none";

		if (loader) loader.style.display = "flex";
		startProgressAnimation();

		await runTestsProcedure();
		await finishProgressAnimation();

		if (loader) loader.style.display = "none";
		if (page3) page3.style.display = "block";
		renderOverallDashboardFromBackend();
		if (selected) selected.style.display = "block";
	});
	localStorage.setItem("selectedTests", JSON.stringify(selectedTests));
}
function goBackToResults() {
	window.history.back();
}

async function runTestsProcedure() {
	try {
		if (!cleanedCsvBlob) {
			alert("Please verify CSV first!");
			return;
		}

		const form = new FormData();
		form.append("headersFile", cleanedCsvBlob);

		const suiteFetches = selectedTests.map(async t => {
			const url = `http://localhost:8080/api/tests/test?path=${encodeURIComponent(t.path)}&ts=${Date.now()}`;

			const res = await fetch(url, {
				method: "GET",
				cache: "no-store",
				headers: {
					"Cache-Control": "no-cache, no-store, must-revalidate",
					"Pragma": "no-cache",
					"Expires": "0"
				}
			});

			const json = await res.json();

			const originalName = t.path.split("/").pop();

			form.append(
				"testFiles",
				new Blob([JSON.stringify(json)], { type: "application/json" }),
				originalName
			);
		});

		await Promise.all(suiteFetches);

		form.append("csvRows", JSON.stringify(cleanedCsvRows));

		const response = await fetch("http://localhost:8080/api/runAll", {
			method: "POST",
			body: form
		});

		backendOutput = await response.json();

		document.dispatchEvent(new Event("testsComplete"));
		renderSelectedSidebar();
		renderOverallDashboardFromBackend();

		setTimeout(() => {
			const overallCard = document.querySelector(".overall-summary-card");
			if (overallCard) overallCard.classList.add("selected");
		}, 50);

	} catch (err) {
		console.error(err);
		resultsContainer.innerHTML = `<div class="error">Error running tests: ${err.message}</div>`;
	}
}

function goHome() {
	window.location.href = "index.html";
}

function handleCookieProceed() {
	if (!cleanedCsvBlob) {
		alert("No valid rows found!");
		return;
	}

	const page1 = document.getElementById("page1");
	const page2 = document.getElementById("page2");
	const page3 = document.getElementById("page3");
	const avail = document.getElementById("available-sidebar");
	const selected = document.getElementById("selected-sidebar");

	if (page1) page1.style.display = "none";
	if (page2) page2.style.display = "block";
	if (page3) page3.style.display = "none";

	if (avail) avail.style.display = "block";
	if (selected) selected.style.display = "none";

	const validCount = document.getElementById("validCount");
	if (validCount) validCount.textContent = totalValidCookies;

	const list = document.getElementById("validatedList");
	if (!list) return;

	list.innerHTML = "";
	cleanedCsvRows.forEach(row => {
		const div = document.createElement("div");
		div.className = "validated-item";
		div.innerHTML = `
      <span class="validated-icon">✔</span>
      ${row.cookieName}
    `;
		list.appendChild(div);
	});
	localStorage.setItem("validatedCSV", JSON.stringify(cleanedCsvRows));
}

function handleCookieCancel() {
	if (cookieValidationResult) cookieValidationResult.innerHTML = "";
	if (verifyBtn) verifyBtn.style.display = "inline-block";
	cleanedCsvRows = [];
	cleanedCsvBlob = null;
	const input = document.getElementById("csvFile");
	if (input) input.value = "";
	if (cookieCounterBadge) cookieCounterBadge.style.display = "none";
}

let bulkSelecting = false;
let missingParents = new Set();
let isAutoSelecting = false;

function isParentAlreadySelected(parentId) {
	return selectedTests.some(t => t.id == parentId);
}
function isParentAlreadySelected(parentId) {
	return selectedTests.some(t => t.id == parentId);
}

async function updateSelectedTests(testPath, isChecked, isBulk = false, suppressParentAlert = false) {

	const res = await fetch(
		`http://localhost:8080/api/tests/test?path=${encodeURIComponent(testPath)}`
	);
	const json = await res.json();
	const testList = Array.isArray(json.testCases) ? json.testCases : [json];
	const tc = testList[0];

	if (!isChecked) {
		selectedTests = selectedTests.filter(t => t.path !== testPath);
		renderSelectedSidebar();
		return;
	}

	if (!selectedTests.some(t => t.path === testPath)) {
		selectedTests.push({ path: testPath, id: tc.id });
	}

	if (tc.parentId && !isParentAlreadySelected(tc.parentId)) {
		const parentPath = findTestPathById(tc.parentId);

		if (parentPath) {
			const parentCheckbox =
				document.querySelector(`.test-checkbox[data-path="${parentPath}"]`);
			if (parentCheckbox) parentCheckbox.checked = true;

			if (!selectedTests.some(t => t.path === parentPath)) {
				selectedTests.push({ path: parentPath, id: tc.parentId });
			}

			const parentName = parentPath.split("/").pop().replace(".json", "");

			if (isBulk) {
				if (!suppressParentAlert) {
					missingParents.add(`"${tc.name}" ➜ requires "${parentName}"`);
				}
			} else {
				alert(`"${tc.name}" requires "${parentName}". Auto-selecting parent.`);
			}
		}
	}

	renderSelectedSidebar();
}

function findTestPathById(id) {
	let foundPath = null;

	function searchFolders(folder, currentPath) {
		if (!folder || foundPath) return;

		if (folder.tests) {
			for (const filename of folder.tests) {
				const filePath = `${currentPath}/${filename}`;

				const xhr = new XMLHttpRequest();
				xhr.open("GET", `http://localhost:8080/api/tests/test?path=${encodeURIComponent(filePath)}`, false);
				xhr.send();

				if (xhr.status === 200) {
					const json = JSON.parse(xhr.responseText);
					const tcList = Array.isArray(json.testCases) ? json.testCases : [json];

					if (tcList.some(t => t.id == id)) {
						foundPath = filePath;
						return;
					}
				}
			}
		}

		if (folder.folders) {
			for (const sub of Object.keys(folder.folders)) {
				searchFolders(folder.folders[sub], `${currentPath}/${sub}`);
			}
		}
	}

	Object.keys(testFiles.folders).forEach(root => {
		searchFolders(testFiles.folders[root], root);
	});

	return foundPath;
}

function renderAvailableTests() {
	if (!availableSidebar) return;

	availableSidebar.innerHTML = "<h3>Available TestSuites</h3>";

	function setRecursiveSelection(container, isChecked, isRootBulk) {
		const testCheckboxes = container.querySelectorAll(".test-checkbox");
		testCheckboxes.forEach(chk => {
			chk.checked = isChecked;
			updateSelectedTests(chk.dataset.path, isChecked, true, isRootBulk);
		});
		const folderCheckboxes = container.querySelectorAll(".folder-checkbox");
		folderCheckboxes.forEach(f => f.checked = isChecked);
	}


	function createFolderElement(folderName, content, parentPath = "", level = 0) {
		const fullPath = parentPath ? `${parentPath}/${folderName}` : folderName;

		const folderDiv = document.createElement("div");
		folderDiv.classList.add("folder");
		folderDiv.style.marginLeft = (level * 12) + "px";

		const header = document.createElement("div");
		header.classList.add("tree-folder-header");

		const isRoot = (level === 0);
		header.innerHTML = `
            <div class="tree-folder-row">
                <span class="folder-arrow">${isRoot ? "▼" : "▶"}</span>
                <input type="checkbox" class="folder-checkbox" data-folder="${fullPath}">
                <span class="folder-icon"></span>
                <span class="folder-label">${folderName}</span>
            </div>
        `;

		const contentDiv = document.createElement("div");
		contentDiv.classList.add("folder-content-tree");
		contentDiv.style.display = isRoot ? "block" : "none";

		const arrow = header.querySelector(".folder-arrow");
		const folderCheckbox = header.querySelector(".folder-checkbox");

		function toggleFolder() {
			const isOpen = contentDiv.style.display === "block";
			contentDiv.style.display = isOpen ? "none" : "block";
			arrow.textContent = isOpen ? "▶" : "▼";
		}

		arrow.addEventListener("click", toggleFolder);
		header.querySelector(".folder-label").addEventListener("click", toggleFolder);


		folderCheckbox.addEventListener("change", (e) => {
			missingParents.clear();
			const isRootBulk = isRoot;

			setRecursiveSelection(contentDiv, e.target.checked, isRootBulk);
			renderSelectedSidebar();

			if (!isRootBulk && missingParents.size > 0) {
				alert(
					"These testcases need parents:\n\n" +
					Array.from(missingParents).join("\n")
				);
			}
		});

		if (content.tests) {
			content.tests.forEach(filename => {
				const testPath = `${fullPath}/${filename}`;

				const testRow = document.createElement("label");
				testRow.classList.add("tree-test-item");
				testRow.style.marginLeft = "24px";

				testRow.innerHTML = `
                    <span class="tree-item-connector"></span>
                    <input type="checkbox" class="test-checkbox" data-path="${testPath}">
                    <span class="file-icon"></span>
                    <span class="file-label">${filename.replace(".json", "")}</span>
                `;

				const chk = testRow.querySelector(".test-checkbox");
				chk.addEventListener("change", (e) => {
					const isChecked = e.target.checked;
					updateSelectedTests(testPath, isChecked);
					const anySelected = contentDiv.querySelector(".test-checkbox:checked");
					folderCheckbox.checked = !!anySelected;
					renderSelectedSidebar();
				});

				contentDiv.appendChild(testRow);
			});
		}

		if (content.folders) {
			Object.keys(content.folders).forEach(sub => {
				const childFolder = createFolderElement(sub, content.folders[sub], fullPath, level + 1);
				contentDiv.appendChild(childFolder);

				const observer = new MutationObserver(() => {
					const anySelected = contentDiv.querySelector(".test-checkbox:checked");
					folderCheckbox.checked = !!anySelected;
				});
				observer.observe(childFolder, { subtree: true, childList: true, attributes: true });
			});
		}

		folderDiv.appendChild(header);
		folderDiv.appendChild(contentDiv);
		return folderDiv;
	}

	selectedTests = [];

	if (testFiles.folders) {
		Object.keys(testFiles.folders).forEach(folder => {
			availableSidebar.appendChild(
				createFolderElement(folder, testFiles.folders[folder], "", 0)
			);
		});
	}
}

function renderSelectedSidebar() {
	if (!selectedSidebar) return;
	selectedSidebar.innerHTML = `
        <div class="sidebar-header-controls">
            ${IS_EXECUTE_PAGE ? `<button id="backToSelectionBtn" class="sidebar-back-btn">⬅ Back</button>` : ``}
            <h3 class="sidebar-title">
              Result
              <span id="filterTrigger" class="filter-icon-wrapper" title="Open Filters">🔍</span>
            </h3>
        </div>

        <div id="filterDropdown" class="filter-dropdown hidden">
            <select id="filter-status">
                <option value="all">All</option>
                <option value="passed">Passed</option>
                <option value="failed">Failed</option>
            </select>
            <button id="apply-filters">Apply</button>
            <button id="reset-filters">Reset</button>
        </div>
    `;

	if (!backendOutput || !backendOutput.suites) {
		return;
	}

	const suites = backendOutput.suites;
	const overall = backendOutput.overall || {};
	const overallCard = document.createElement("div");
	overallCard.classList.add("testcase-card", "selected");

	let passedExec = overall.passedExecutionsObserved ?? 0;
	let totalExec = overall.totalExecutionsObserved ?? 0;

	overallCard.innerHTML = `
        <div class="testcase-card-title">Overall Summary</div>
        <div class="testcase-card-stats">${passedExec}/${totalExec} executions passed</div>
    `;

	overallCard.addEventListener("click", () => {
		document.querySelectorAll(".testcase-card").forEach(c => c.classList.remove("selected"));
		overallCard.classList.add("selected");
		renderOverallDashboardFromBackend();
	});

	selectedSidebar.appendChild(overallCard);

	Object.keys(suites).forEach(suiteKey => {
		if (suiteKey.includes("/")) return;

		const parentSuite = suiteKey;
		const suiteSummary = suites[parentSuite];
		const childFolders = suiteSummary.childFolders || {};

		const total = suiteSummary.totalCookies ?? 0;
		const passed = suiteSummary.passedCookies ?? 0;
		const unique = suiteSummary.uniqueTestcases ?? 0;

		const parentWrapper = document.createElement("div");
		parentWrapper.classList.add("tree-parent-wrapper");

		const parentCard = document.createElement("div");
		parentCard.classList.add("testcase-card");
		parentCard.dataset.status = (passed === total && total > 0) ? "passed" : "failed";

		const hasChildren = Object.keys(childFolders).length > 0;
		const arrowHtml = hasChildren ? `<span class="tree-arrow">▶</span>` : "";

		parentCard.innerHTML = `
            <div class="tree-parent-header">
                ${arrowHtml}
                <div class="testcase-card-title">${parentSuite}</div>
            </div>
            <div class="testcase-card-stats">${passed}/${total} accounts passed</div>
            <div class="testcase-card-stats secondary">${unique} testcases</div>
        `;

		parentWrapper.appendChild(parentCard);

		const childrenContainer = document.createElement("div");
		childrenContainer.classList.add("tree-children", "collapsed");
		parentWrapper.appendChild(childrenContainer);

		parentCard.addEventListener("click", () => {
			document.querySelectorAll(".testcase-card").forEach(c => c.classList.remove("selected"));
			parentCard.classList.add("selected");
			showTestResultsFromBackend(parentSuite);
		});

		selectedSidebar.appendChild(parentWrapper);

		if (hasChildren) {
			const arrow = parentCard.querySelector(".tree-arrow");
			arrow.addEventListener("click", (e) => {
				e.stopPropagation();
				childrenContainer.classList.toggle("collapsed");
				arrow.textContent = childrenContainer.classList.contains("collapsed") ? "▶" : "▼";
			});
		}
		Object.keys(childFolders).forEach(childName => {
			const child = childFolders[childName];
			const uniqueChild = child.uniqueTestcases ?? 0;

			const parentDetails = backendOutput.details[parentSuite] || [];
			let totalAcc = 0, passedAcc = 0;

			parentDetails.forEach(account => {
				const cd = backendOutput.cookies[account.uniqueId].childFolders || {};
				if (cd[childName]) {
					const f = cd[childName];
					if (f.failedExecutionsObserved === 0 && f.passedExecutionsObserved > 0)
						passedAcc++;
				}
				totalAcc++;
			});

			const childCard = document.createElement("div");
			childCard.classList.add("testcase-card", "tree-child-card");

			childCard.dataset.status =
				(passedAcc === totalAcc && totalAcc > 0) ? "passed" : "failed";

			childCard.innerHTML = `
                <div class="testcase-card-title">${childName}</div>
                <div class="testcase-card-stats">${passedAcc}/${totalAcc} accounts passed</div>
                <div class="testcase-card-stats secondary">${uniqueChild} testcases</div>
            `;

			childCard.addEventListener("click", (e) => {
				e.stopPropagation();
				document.querySelectorAll(".testcase-card").forEach(c => c.classList.remove("selected"));
				childCard.classList.add("selected");
				showChildSuiteResults(parentSuite, childName);
			});

			childrenContainer.appendChild(childCard);
		});
	});

	const filterDropdown = selectedSidebar.querySelector("#filterDropdown");
	const filterTrigger = selectedSidebar.querySelector("#filterTrigger");
	const filterStatus = selectedSidebar.querySelector("#filter-status");

	filterTrigger?.addEventListener("click", () => {
		filterDropdown?.classList.toggle("hidden");
	});

	selectedSidebar.querySelector("#apply-filters")?.addEventListener("click", () => {
		document.querySelectorAll(".testcase-card").forEach(card => {
			const title = card.querySelector(".testcase-card-title")?.textContent;
			if (title === "Overall Summary") {
				card.style.display = "";
				return;
			}

			const match = filterStatus.value === "all" || card.dataset.status === filterStatus.value;
			card.style.display = match ? "" : "none";
		});

		filterDropdown?.classList.add("hidden");
	});
	selectedSidebar.querySelector("#reset-filters")?.addEventListener("click", () => {
		filterStatus.value = "all";
		document.querySelectorAll(".testcase-card").forEach(card => {
			card.style.display = "";
		});

		filterDropdown?.classList.add("hidden");
	});
}

function showChildSuiteResults(parentSuite, childName) {
	const childSummary = backendOutput.suites[parentSuite]?.childFolders?.[childName];

	if (!childSummary) {
		resultsContainer.innerHTML = `
            <div style="text-align:center; padding:20px; color:#666;">
                No results found for ${childName}
            </div>`;
		return;
	}

	const parentDetails = backendOutput.details?.[parentSuite] || [];
	let childDetails = [];

	parentDetails.forEach(cookieEntry => {
		const filtered = cookieEntry.results.filter(tc =>
			tc.path && tc.path.toLowerCase().includes(childName.toLowerCase())
		);

		if (filtered.length > 0) {
			childDetails.push({
				...cookieEntry,
				results: filtered
			});
		}
	});

	if (childDetails.length === 0) {
		resultsContainer.innerHTML = `
            <div style="text-align:center; padding:20px; color:#666;">
                No results found for ${childName}
            </div>`;
		return;
	}

	let passedAccounts = 0;
	let failedAccounts = 0;

	childDetails.forEach(acc => {
		const allPassed = acc.results.every(tc => getTestcaseStatus(tc) === "PASS");

		if (allPassed) passedAccounts++;
		else failedAccounts++;
	});

	const suiteKey = parentSuite + "/" + childName;
	backendOutput.details[suiteKey] = childDetails;

	showTestResultsFromBackend(suiteKey, false);
	const summaryCards = document.querySelectorAll(".summary-card");

	if (summaryCards.length === 2) {
		summaryCards[0].querySelector("div:last-child").textContent = passedAccounts;
		summaryCards[1].querySelector("div:last-child").textContent = failedAccounts;
	}
}

function renderOverallDashboardFromBackend() {
	if (!resultsContainer) return;

	if (!backendOutput) {
		resultsContainer.innerHTML = `
      <div class="no-results">No backend result available. Run tests first.</div>`;
		return;
	}

	const overall = backendOutput.overall || {};
	const tcSummary = computeTestcasePassFailSummary();

	resultsContainer.innerHTML = `
    <div class="overall-dashboard">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:15px;">
        <h2 style="margin:0;">Overall Execution Summary</h2>
        <button id="downloadReportBtn" style="padding:8px 14px; border-radius:6px; background:#2563eb; color:white; border:none; cursor:pointer; font-size:14px;">
          📄 Download Report
        </button>
      </div>

      <div id="overall-exec-time" style="text-align:center; color:#6b7280; margin-bottom:15px; font-size:15px;">
        Overall Execution Time: ${overall.executionTimeSec ?? 0}s
      </div>

      <div class="dashboard-grid">
        <div class="stat-card"><div class="stat-card-title">Test Suites</div><div class="stat-card-value">${overall.totalSuites ?? 0}</div></div>
        <div class="stat-card"><div class="stat-card-title">Total Accounts</div><div class="stat-card-value">${overall.totalCookies ?? 0}</div></div>
        <div class="stat-card"><div class="stat-card-title">Total Testcases</div><div class="stat-card-value">${overall.uniqueTestcases ?? 0}</div></div>
        <div class="stat-card"><div class="stat-card-title">Passed Testcases</div><div class="stat-card-value">${tcSummary.passed}</div></div>
        <div class="stat-card"><div class="stat-card-title">Failed Testcases</div><div class="stat-card-value">${tcSummary.failed}</div></div>
        <div class="stat-card"><div class="stat-card-title">Skipped Testcases</div><div class="stat-card-value">${tcSummary.skipped}</div></div>
        <div class="stat-card"><div class="stat-card-title">Executions Observed</div><div class="stat-card-value">${overall.totalExecutionsObserved ?? 0}</div></div>
        <div class="stat-card"><div class="stat-card-title">Passed Executions</div><div class="stat-card-value">${overall.passedExecutionsObserved ?? 0}</div></div>
        <div class="stat-card"><div class="stat-card-title">Execution Pass Rate</div><div class="stat-card-value">${overall.executionPassRateObserved ?? 0}%</div></div>
      </div>

      <div class="dashboard-chart-area" style="margin-top:25px; text-align:center;">
        <canvas id="overallExecutionChart" style="max-width:420px; margin:auto;"></canvas>
      </div>

      <hr style="margin:40px 0; border:0; border-top:2px solid #e5e7eb;">
      <h2 style="text-align:center; margin-bottom:15px;">Account-Level Summary</h2>

      <section id="cookieSummarySection" class="cookie-summary-section">
        <div class="cookie-summary-dropdown">
          <div class="cookie-filter-buttons">
            <button class="cookie-filter-btn active" data-filter="all">All Accounts</button>
            <button class="cookie-filter-btn" data-filter="passed">Passed Accounts</button>
            <button class="cookie-filter-btn" data-filter="failed">Failed Accounts</button>
            <button class="cookie-filter-btn" data-filter="skipped">Skipped Accounts</button>

          </div>
          <select id="cookieSummaryDropdown"><option value="">-- Select an Account --</option></select>
        </div>

        <div id="cookieSummaryHeader" class="cookie-summary-header" style="display:none;">
          <div class="result-box pass-box">Passed: <span id="cookiePassedCount">0</span></div>
          <div class="result-box fail-box">Failed: <span id="cookieFailedCount">0</span></div>
          <div class="result-box skip-box">Skipped: <span id="cookieSkippedCount">0</span></div>
          <div class="result-box total-box">Total: <span id="cookieTotalCount">0</span></div>
        </div>
        <div id="cookieSummaryFilters" class="cookie-summary-filters" style="display:none;">
          <select id="cookieFilterSuite"><option value="all">All TestSuites</option></select>
          <select id="cookieFilterStatus"><option value="all">All Status</option><option value="PASS">Passed</option><option value="FAIL">Failed</option><option value="SKIPPED">Skipped</option>
</select>
        </div>

        <div id="cookieSummaryTableArea" class="cookie-summary-table"></div>
      </section>
    </div>
  `;

	try {
		if (currentChart) currentChart.destroy();
	} catch (e) { /* ignore */ }

	const ctx = document.getElementById("overallExecutionChart")?.getContext("2d");
	if (ctx) {
		const passed = overall.passedExecutionsObserved ?? 0;
		const failed = overall.failedExecutionsObserved ?? 0;
		const skipped = overall.skippedExecutionsObserved ?? 0;

		currentChart = new Chart(ctx, {
			type: "doughnut",
			data: {
				labels: ["Passed Executions", "Failed Executions", "Skipped Executions"],
				datasets: [{
					data: [passed, failed, skipped],
					backgroundColor: ["#10b981", "#ef4444", "#FFD700"],
					borderWidth: 0
				}]
			},
			options: {
				cutout: "60%",
				plugins: { legend: { position: "bottom" } }
			}
		});
	}

	const cookieDropdown = document.getElementById("cookieSummaryDropdown");

	function refreshCookieDropdown(filter) {
		const cookiePassMap = getCookiePassFailMap();

		cookieDropdown.innerHTML = `<option value="">-- Select an Account --</option>`;

		Object.keys(cookiePassMap).forEach(cid => {
			const obj = cookiePassMap[cid];

			const statuses = { ...obj };
			const isFailed = statuses.failed;
			const isAllSkipped = statuses.skipped && !statuses.passed;
			const isPassed = statuses.passed && !statuses.failed;

			if (filter === "passed" && !isPassed) return;
			if (filter === "failed" && !isFailed) return;
			if (filter === "skipped" && !isAllSkipped) return;

			const opt = document.createElement("option");
			opt.value = cid;
			opt.textContent = obj.cookieName;
			cookieDropdown.appendChild(opt);
		});
	}


	refreshCookieDropdown("all");

	cookieDropdown?.addEventListener("change", function() {
		if (this.value) renderCookieSummaryFromBackend(this.value);
	});

	document.querySelectorAll(".cookie-filter-btn").forEach(btn => {
		btn.addEventListener("click", () => {
			document.querySelectorAll(".cookie-filter-btn").forEach(b => b.classList.remove("active"));
			btn.classList.add("active");
			const filter = btn.dataset.filter;
			refreshCookieDropdown(filter);
		});
	});

	document.getElementById("downloadReportBtn")
		?.addEventListener("click", downloadReport);
}

function renderCookieSummaryFromBackend(cookieKey) {
	const tableArea = document.getElementById("cookieSummaryTableArea");
	const header = document.getElementById("cookieSummaryHeader");
	const filterSuite = document.getElementById("cookieFilterSuite");
	const filterStatus = document.getElementById("cookieFilterStatus");

	if (!tableArea || !header || !filterSuite || !filterStatus) return;

	const cookieMeta = backendOutput?.cookies?.[cookieKey];
	if (!cookieMeta) {
		tableArea.innerHTML = `<div class="no-results">No data found for cookie</div>`;
		return;
	}

	const suiteKeys = cookieMeta.suites || [];
	let allTestcases = [];

	suiteKeys.forEach(suiteKey => {
		const suiteDetails = backendOutput.details?.[suiteKey] || [];
		suiteDetails.forEach(cookieEntry => {
			if (cookieEntry.uniqueId === cookieKey) {
				cookieEntry.results.forEach(tc => {
					allTestcases.push({
						id: tc.id,
						name: tc.name,
						suiteKey,
						status: getTestcaseStatus(tc)
					});
				});
			}
		});
	});

	if (allTestcases.length === 0) {
		tableArea.innerHTML = `<div class="no-results">No testcase data for this cookie</div>`;
		return;
	}

	header.style.display = "flex";
	document.getElementById("cookiePassedCount").textContent =
		allTestcases.filter(t => t.status === "PASS").length;
	document.getElementById("cookieFailedCount").textContent =
		allTestcases.filter(t => t.status === "FAIL").length;

	document.getElementById("cookieSkippedCount").textContent =
		allTestcases.filter(t => t.status === "SKIPPED").length;

	document.getElementById("cookieTotalCount").textContent =
		allTestcases.length;
	filterSuite.innerHTML = `<option value="all">All TestSuites</option>`;
	suiteKeys.forEach(sk => {
		const opt = document.createElement("option");
		opt.value = sk;
		opt.textContent = sk;
		filterSuite.appendChild(opt);
	});

	document.getElementById("cookieSummaryFilters").style.display = "flex";

	function renderTable() {
		const suiteFilter = filterSuite.value;
		const statusFilter = filterStatus.value;
		let filtered = allTestcases;

		if (suiteFilter !== "all")
			filtered = filtered.filter(tc => tc.suiteKey === suiteFilter);
		if (statusFilter !== "all")
			filtered = filtered.filter(tc => tc.status === statusFilter);

		tableArea.innerHTML = `
      <table class="cookie-summary-table-inner">
        <thead>
          <tr>
            <th>#</th><th>Testcase ID</th><th>Name</th><th>TestSuite</th><th>Status</th>
          </tr>
        </thead>
        <tbody>
          ${filtered.map((tc, i) => `
            <tr>
              <td>${i + 1}</td>
              <td>${tc.id}</td>
              <td>${tc.name}</td>
              <td>${tc.suiteKey}</td>
              <td>
  <span class="status-badge 
    ${tc.status === "PASS" ? "pass" :
				tc.status === "FAIL" ? "fail" :
					"skipped"
			}">
    ${tc.status}
  </span>
</td>

 </tr>
          `).join("")}
        </tbody>
      </table>
    `;
	}

	renderTable();
	filterSuite.onchange = filterStatus.onchange = renderTable;
}
function computeTestcasePassFailSummary() {
	let summary = {
		total: 0,
		passed: 0,
		failed: 0,
		skipped: 0
	};

	if (!backendOutput || !backendOutput.details)
		return summary;

	let testcaseMap = {};

	Object.values(backendOutput.details).forEach(suiteDetails => {
		suiteDetails.forEach(cookie => {
			cookie.results.forEach(tc => {
				const id = tc.id;
				const status = getTestcaseStatus(tc);

				if (!testcaseMap[id]) {
					testcaseMap[id] = {
						hasPass: false,
						hasFail: false,
						hasSkip: false
					};
				}

				if (status === "PASS") testcaseMap[id].hasPass = true;
				if (status === "FAIL") testcaseMap[id].hasFail = true;
				if (status === "SKIPPED") testcaseMap[id].hasSkip = true;
			});
		});
	});

	summary.total = Object.keys(testcaseMap).length;

	Object.values(testcaseMap).forEach(tc => {
		if (tc.hasFail) summary.failed++;
		else if (tc.hasSkip && !tc.hasFail) summary.skipped++;
		else if (tc.hasPass && !tc.hasFail && !tc.hasSkip) summary.passed++;
	});

	return summary;
}

function getCookiePassFailMap() {
	let map = {};
	if (!backendOutput || !backendOutput.details) return map;

	Object.values(backendOutput.details).forEach(suiteDetails => {
		suiteDetails.forEach(cookieEntry => {
			const id = cookieEntry.uniqueId;
			if (!map[id]) {
				map[id] = {
					cookieName: cookieEntry.cookieName || id,
					passed: false,
					failed: false,
					skipped: false
				};
			}

			const statuses = cookieEntry.results.map(tc => getTestcaseStatus(tc));


			if (statuses.some(s => s === "FAIL")) {
				map[id].failed = true;
				map[id].passed = false;
				map[id].skipped = false;
				return;
			}

			if (statuses.every(s => s === "SKIPPED")) {
				map[id].skipped = true;
				map[id].passed = false;
				return;
			}

			if (statuses.some(s => s === "PASS")) {
				map[id].passed = true;
				map[id].skipped = false;
			}
		});
	});

	return map;
}


function renderExecutionBlocks(results) {

  const executionCount = results.filter(r => r.type === "EXECUTION").length;

  // If only one execution → render normally (no boxes)
  if (executionCount <= 1) {
    return results
      .filter(r => r.type !== "EXECUTION")
      .map(r => `
        <div class="check-item">
          <span class="check-icon">
            ${r.status === "PASS" ? "✔" :
              r.status === "FAIL" ? "✖" : "⏭"}
          </span>
          <div class="check-content">
            <div class="check-type">${escapeHtml(r.type)}</div>
            <div class="check-message">${escapeHtml(r.message || "")}</div>
          </div>
        </div>
      `).join("");
  }

  // Multiple executions → group into blocks
  let html = "";
  let currentBlock = null;

  results.forEach(r => {

    if (r.type === "EXECUTION") {

      if (currentBlock) {
        html += buildExecutionCard(currentBlock);
      }

      currentBlock = {
        endpoint: r.message.replace("Endpoint: ", ""),
        checks: []
      };

    } else if (currentBlock) {
      currentBlock.checks.push(r);
    }

  });

  if (currentBlock) {
    html += buildExecutionCard(currentBlock);
  }

  return html;
}
function buildExecutionCard(block) {

  return `
    <div class="execution-card">
      <div class="execution-endpoint">
        🔗 ${escapeHtml(block.endpoint)}
      </div>

      ${block.checks.map(r => `
        <div class="execution-check ${r.status.toLowerCase()}">
          <span class="check-icon">
            ${r.status === "PASS" ? "✔" : r.status === "FAIL" ? "✖" : "⏭"}
          </span>
          <div>
            <div class="check-type">${escapeHtml(r.type)}</div>
            <div class="check-message">${escapeHtml(r.message || "")}</div>
          </div>
        </div>
      `).join("")}
    </div>
  `;
}
function showTestResultsFromBackend(suiteKey, isParent = false) {
	if (!resultsContainer) return;

	const details = backendOutput.details?.[suiteKey];

	if (!details) {
		resultsContainer.innerHTML = `
          <div style="text-align:center; color:#666;">
            No results found for TestSuite: ${suiteKey}
          </div>`;
		return;
	}

	let passed = 0, failed = 0, skipped = 0;

	details.forEach(cookie => {
		const statuses = cookie.results.map(tc => getTestcaseStatus(tc));

		if (statuses.some(s => s === "FAIL")) {
			failed++;
		}
		else if (statuses.every(s => s === "SKIPPED")) {
			skipped++;
		}
		else {
			passed++;
		}
	});


	resultsContainer.innerHTML = `
      <h2 style="text-align:center; margin-bottom:15px;">
        TestSuite: ${suiteKey}
      </h2>

	  <div class="suite-summary-cards" style="display:flex; justify-content:center; gap:20px; margin-bottom:20px;">

	    <div class="summary-card"
	         style="background:#f0fdf4; border-left:6px solid #10b981; padding:15px 20px; border-radius:12px; min-width:160px; text-align:center;">
	      <div style="color:#065f46; font-size:16px; font-weight:600;">✔ Accounts Passed</div>
	      <div style="color:#064e3b; font-size:26px; font-weight:700;">${passed}</div>
	    </div>

	    <div class="summary-card"
	         style="background:#fef2f2; border-left:6px solid #ef4444; padding:15px 20px; border-radius:12px; min-width:160px; text-align:center;">
	      <div style="color:#b91c1c; font-size:16px; font-weight:600;">✘ Accounts Failed</div>
	      <div style="color:#991b1b; font-size:26px; font-weight:700;">${failed}</div>
	    </div>

	    <div class="summary-card"
	         style="background:#fef9c3; border-left:6px solid #d97706; padding:15px 20px; border-radius:12px; min-width:160px; text-align:center;">
	      <div style="color:#b45309; font-size:16px; font-weight:600;">⏭ Accounts Skipped</div>
	      <div style="color:#92400e; font-size:26px; font-weight:700;">${skipped}</div>
	    </div>

	  </div>

      <div class="table-wrapper">
        <table class="cookie-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Account Name</th>
              <th>Status</th>
              <th>Passed</th>
              <th>Failed</th>
              <th>Skipped</th>
              <th>Total</th>
              <th>Execution Time</th>
            </tr>
          </thead>
          <tbody id="cookie-table-body"></tbody>
        </table>
      </div>
    `;

	const tbody = document.getElementById("cookie-table-body");

	details.forEach((cookie, idx) => {
		const passedCount = cookie.results.filter(r => getTestcaseStatus(r) === "PASS").length;
		const failedCount = cookie.results.filter(r => getTestcaseStatus(r) === "FAIL").length;
		const skippedCount = cookie.results.filter(r => getTestcaseStatus(r) === "SKIPPED").length;

		let status;
		if (failedCount > 0) {
			status = "FAIL";
		} else if (passedCount > 0) {
			status = "PASS";
		} else {
			status = "SKIPPED";
		}

        const tr = document.createElement("tr");
        tr.classList.add("cookie-row");
        tr.title = "Click to view testcase results";

		tr.dataset.cookieIdx = idx;

		tr.innerHTML = `
          <td>${idx + 1}</td>
          <td>${escapeHtml(cookie.cookieName)}</td>
          <td><span class="status-badge ${status.toLowerCase()}">${status}</span></td>
          <td>${passedCount}</td>
          <td>${failedCount}</td>
          <td>${skippedCount}</td>
          <td>${cookie.results.length}</td>
          <td>${cookie.executionTimeSec} seconds</td>
        `;

		tr.addEventListener("click", (e) => {
			if (e.target.closest(".copy-btn")) return;
			openCookieDetailsFromBackend(suiteKey, cookie, cookie.cookieName, idx);
		});

		tbody.appendChild(tr);
	});
}

function openCookieDetailsFromBackend(suiteKey, cookie, cookieName, cookieIdx) {
	document
		.querySelectorAll(".cookie-row")
		.forEach(row => row.classList.remove("selected"));
	document
		.querySelector(`[data-cookie-idx="${cookieIdx}"]`)
		?.classList.add("selected");

	if (!sidePanel || !sidePanelOverlay || !sidePanelBody || !sidePanelTitle) return;

	sidePanelTitle.textContent = `TestSuite: ${suiteKey}`;
	const totalTests = cookie.results.length;
	const passedTests = cookie.results.filter(r => getTestcaseStatus(r) === "PASS").length;
	const failedTests = cookie.results.filter(r => getTestcaseStatus(r) === "FAIL").length;
	const skippedTests = cookie.results.filter(r => getTestcaseStatus(r) === "SKIPPED").length;

	sidePanelBody.innerHTML = `
    <div class="cookie-detail-section">
      <div class="cookie-detail-label">Account Name</div>
      <div class="cookie-detail-value">${escapeHtml(cookieName)}</div>
    </div>

    <div class="cookie-detail-section">
      <div class="cookie-detail-label">Test Results (${totalTests} Total)</div>
      <div class="tests-summary" style="margin-bottom: 15px;">
        <span class="pass-count">${passedTests} Passed</span> • 
        <span class="fail-count">${failedTests} Failed</span> •
        <span class="skip-count">${skippedTests} Skipped</span>
      </div>
    </div>

    <div class="filter-bar">
      <button class="filter-btn active" data-filter="all">All</button>
      <button class="filter-btn" data-filter="passed">Passed</button>
      <button class="filter-btn" data-filter="failed">Failed</button>
      <button class="filter-btn" data-filter="skipped">Skipped</button>
    </div>

    <div id="testcase-list"></div>
  `;

	const testcaseList = sidePanelBody.querySelector("#testcase-list");

	function renderTestcases(filter) {
		testcaseList.innerHTML = "";
		cookie.results.forEach((tc) => {

			const status = getTestcaseStatus(tc);

			if (filter === "passed" && status !== "PASS") return;
			if (filter === "failed" && status !== "FAIL") return;
			if (filter === "skipped" && status !== "SKIPPED") return;

			const wrapper = document.createElement("div");
			wrapper.className = "tc-wrapper";

			wrapper.innerHTML = `
        <div class="tc-row view-mode">
          <div class="tc-left">
            <span class="tc-id">TC-${tc.id}</span>
            <span class="tc-name">${escapeHtml(tc.name)}</span>
          </div>
          <span class="tc-status ${status.toLowerCase()}">
            ${status === "PASS" ? "✔ PASS" : status === "FAIL" ? "✖ FAIL" : "⏭ SKIPPED"}
          </span>
        </div>

        <div class="tc-card card-mode hidden">
          <div class="tc-detail-header">
            <div>
              <div class="tc-detail-id">TC-${tc.id}</div>
              <div class="tc-detail-name">${escapeHtml(tc.name)}</div>
            </div>
            <span class="tc-detail-status ${status.toLowerCase()}">
              ${status === "PASS" ? "✔ PASS" : status === "FAIL" ? "✖ FAIL" : "⏭ SKIPPED"}
            </span>
          </div>

          <div class="tc-detail-url">
            ${escapeHtml(tc.fullUrl || tc.endpoint || "")}
          </div>

          <div class="tc-summary-line">
            Total: ${tc.summary.totalChecks} | 
            Passed: ${tc.summary.passed} | 
            Failed: ${tc.summary.failed}
          </div>

          <button class="json-preview-btn" data-tcid="${tc.id}"
                  data-tcname="${escapeHtml(tc.name)}" data-tcpath="${tc.path}">
            View Validation JSON
          </button>

         <div class="tc-checks">
           ${renderExecutionBlocks(tc.results)}
         </div>

        </div>
      `;

			const row = wrapper.querySelector(".tc-row");
			const card = wrapper.querySelector(".tc-card");

			row.addEventListener("click", () => {
				row.classList.add("hidden");
				card.classList.remove("hidden");
			});

			card.addEventListener("click", (e) => {
				if (e.target.closest(".json-preview-btn")) return;
				if (e.target.closest(".check-item")) return;
				card.classList.add("hidden");
				row.classList.remove("hidden");
			});

			const jsonBtn = wrapper.querySelector(".json-preview-btn");
			jsonBtn.addEventListener("click", async (e) => {
				e.stopPropagation();

				const testcaseId = parseInt(jsonBtn.dataset.tcid, 10);
				const testcaseName = jsonBtn.dataset.tcname;
				const testcasePath = jsonBtn.dataset.tcpath
				await showStaticTestJson(null, testcasePath, testcaseId, testcaseName);
			});

			testcaseList.appendChild(wrapper);
		});
	}

	renderTestcases("all");

	const filterButtons = sidePanelBody.querySelectorAll(".filter-btn");
	filterButtons.forEach(btn => btn.addEventListener("click", () => {
		filterButtons.forEach(b => b.classList.remove("active"));
		btn.classList.add("active");
		renderTestcases(btn.dataset.filter);
	}));

	sidePanel.classList.add("open");
	sidePanelOverlay.classList.add("active");
}

async function showStaticTestJson(agent, testPath, testcaseId, testcaseName) {
	if (!jsonModal || !jsonModalTitle || !jsonModalBody) return;

	try {
		const finalPath = `${testPath}/${testcaseName}.json`;
		const res = await fetch(
			`http://localhost:8080/api/tests/test?path=${encodeURIComponent(finalPath)}`
		);

		if (!res.ok) throw new Error("Failed to fetch test JSON");

		const data = await res.json();

		let found = null;

		if (Array.isArray(data.testCases)) {
			found = data.testCases.find(tc =>
				tc.id === testcaseId || tc.name === testcaseName
			);
		}

		else if (data.id === testcaseId || data.name === testcaseName) {
			found = data;
		}

		if (!found) {
			jsonModalTitle.textContent = "Test JSON";
			jsonModalBody.textContent = "Testcase not found in JSON file.";
		} else {
			jsonModalTitle.textContent = `${found.name} — Test JSON`;
			jsonModalBody.innerHTML = syntaxHighlight(found);
		}

		jsonModal.classList.add("active");

	} catch (err) {
		jsonModalTitle.textContent = "Error";
		jsonModalBody.textContent = err.message;
		jsonModal.classList.add("active");
	}
}

async function downloadReport() {
	try {
		if (!backendOutput) {
			alert("Run the tests first!");
			return;
		}

		const payload = { backendOutput, csvRows: cleanedCsvRows };

		const resp = await fetch("http://localhost:8080/api/downloadReport", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(payload)
		});

		if (!resp.ok) throw new Error(`Server error: ${resp.status}`);

		let fileName = "Testiq_Report.pdf";
		const disp = resp.headers.get("Content-Disposition");
		const match = disp?.match(/filename=\"(.+)\"/);
		if (match) fileName = match[1];

		const blob = await resp.blob();
		const blobUrl = window.URL.createObjectURL(blob);

		const a = document.createElement("a");
		a.href = blobUrl;
		a.download = fileName;
		a.click();

		setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);

	} catch (err) {
		console.error("Download failed:", err);
		alert("Failed to download: " + err.message);
	}
}

function syntaxHighlight(json) {
	try {
		json = JSON.stringify(json, null, 2);
	} catch (e) {
		json = String(json);
	}
	json = json
		.replace(/&/g, "&amp;")
		.replace(/</g, "&lt;")
		.replace(/>/g, "&gt;");
	return json.replace(
		/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g,
		function(match) {
			let cls = "json-number";
			if (/^"/.test(match)) cls = /:$/.test(match) ? "json-key" : "json-string";
			else if (/true|false/.test(match)) cls = "json-boolean";
			else if (/null/.test(match)) cls = "json-null";
			return `<span class="${cls}">${match}</span>`;
		}
	);
}

function escapeHtml(s) {
	if (s === null || s === undefined) return "";
	return String(s)
		.replace(/&/g, "&amp;")
		.replace(/</g, "&lt;")
		.replace(/>/g, "&gt;");
}

function closeSidePanel() {
	if (!sidePanel || !sidePanelOverlay) return;
	sidePanel.classList.remove("open");
	sidePanelOverlay.classList.remove("active");
	document
		.querySelectorAll(".cookie-row.selected")
		.forEach(row => row.classList.remove("selected"));
}

function toggleResultSidebar() {
	const sidebar = document.getElementById("selected-sidebar");
	if (sidebar) sidebar.classList.toggle("collapsed");
}
async function loadResultsPage() {
	if (!resultsContainer) return;

	const params = new URLSearchParams(window.location.search);
	const runId = params.get("runId");

	if (!runId) {
		resultsContainer.innerHTML = `<div class="no-results">No runId provided.</div>`;
		return;
	}

	try {
		const resp = await fetch(`http://localhost:8080/api/results/runs/${encodeURIComponent(runId)}`);
		if (!resp.ok) throw new Error("Failed to load results");

		backendOutput = await resp.json();
		selectedTests = [];
		if (backendOutput.suites) {
			Object.keys(backendOutput.suites).forEach(key => {
				selectedTests.push({ path: key });
			});
		}

		renderSelectedSidebar();
		renderOverallDashboardFromBackend();

		const ss = document.getElementById("selected-sidebar");
		if (ss) ss.style.display = "block";

	} catch (e) {
		resultsContainer.innerHTML = `<div class="error">${e.message}</div>`;
	}
}
function getTestcaseStatus(tc) {

	if (tc.results && tc.results.some(r => r.status === "FAIL")) {
		return "FAIL";
	}
	if (tc.skipped === true) {
		return "SKIPPED";
	}
	if (tc.results && tc.results.some(r => r.status === "SKIPPED")) {
		return "SKIPPED";
	}
	if (tc.summary && tc.summary.failed > 0) {
		return "FAIL";
	}
	return "PASS";
}


const csvInput = document.getElementById("csvFile");
csvInput?.addEventListener("change", () => {
	if (cookieValidationResult) {
		cookieValidationResult.innerHTML = "";
		cookieValidationResult.style.display = "none";
	}

	cleanedCsvRows = [];
	cleanedCsvBlob = null;

	if (csvInput.files.length > 0) {
		verifyBtn.style.display = "inline-block";
	} else {
		verifyBtn.style.display = "none";
	}
});


document.addEventListener("DOMContentLoaded", () => {
	closeJsonModal?.addEventListener("click", () => {
		jsonModal?.classList.remove("active");
	});

	window.addEventListener("click", (e) => {
		if (e.target === jsonModal) {
			jsonModal.classList.remove("active");
		}
	});
	sidePanelClose?.addEventListener("click", closeSidePanel);
	sidePanelOverlay?.addEventListener("click", closeSidePanel);

	document.addEventListener("click", (e) => {
		const btn = e.target.closest("#backToOverall");
		if (btn) {
			renderOverallDashboardFromBackend();
		}
	});
	document.getElementById("downloadSampleCsv")?.addEventListener("click", () => {
		const a = document.createElement("a");
		a.href = "/assets/docs/Template.csv";
		a.download = "Template.csv";
		document.body.appendChild(a);
		a.click();
		document.body.removeChild(a);
	});
	if (IS_EXECUTE_PAGE) {
		loadDynamicSuites();
		renderAvailableTests();
		renderSelectedSidebar();
	} else if (IS_RESULTS_PAGE) {
		loadResultsPage();
	}

	const toggleAvailable = document.getElementById("toggleAvailable");
	const toggleSelected = document.getElementById("toggleSelected");
	toggleAvailable?.addEventListener("click", () => {
		if (availableSidebar) availableSidebar.classList.toggle("collapsed");
	});
	toggleSelected?.addEventListener("click", () => {
		if (selectedSidebar) selectedSidebar.classList.toggle("collapsed");
	});
});


document.addEventListener("click", (e) => {
	const btn = e.target.closest("#backToSelectionBtn");
	if (!btn) return;
	document.getElementById("page3").style.display = "none";
	document.getElementById("page2").style.display = "block";
	document.getElementById("available-sidebar").style.display = "block";
	document.getElementById("selected-sidebar").style.display = "none";
});
document.getElementById("backToCSVUpload")?.addEventListener("click", () => {
	document.getElementById("page2").style.display = "none";
	document.getElementById("page1").style.display = "block";
	document.getElementById("available-sidebar").style.display = "none";
	document.getElementById("selected-sidebar").style.display = "none";
	document.getElementById("verifyCookiesBtn").style.display = "none";
});

document.addEventListener("click", async (e) => {
	const btn = e.target.closest(".copy-btn");
	if (!btn) return;

	e.stopPropagation();
	const text = btn.dataset.cookie;

	try {
		await navigator.clipboard.writeText(text);
		btn.textContent = "Copied!";
		setTimeout(() => btn.textContent = "Copy", 1000);
	} catch (err) {
		console.error("Clipboard copy failed:", err);
		alert("Failed to copy cookie value");
	}
});



