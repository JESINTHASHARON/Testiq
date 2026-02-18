
document.getElementById("filterSelect").addEventListener("change", loadResultsFiltered);
document.getElementById("startDate").addEventListener("change", loadResultsFiltered);
document.getElementById("endDate").addEventListener("change", loadResultsFiltered);
document.getElementById("sortSelect").addEventListener("change", loadResultsFiltered);
document.getElementById("searchInput").addEventListener("input", loadResultsFiltered);

document.getElementById("resetFiltersBtn")?.addEventListener("click", () => {
	document.getElementById("filterSelect").value = "";
	document.getElementById("sortSelect").value = "desc";
	document.getElementById("searchInput").value = "";
	document.getElementById("startDate").value = "";
	document.getElementById("endDate").value = "";

	loadResultsFiltered(); 
});

document.getElementById("startDate").addEventListener("focus", function() {
	this.type = "date";
});

document.getElementById("endDate").addEventListener("focus", function() {
	this.type = "date";
});

document.getElementById("startDate").addEventListener("blur", function() {
	if (!this.value) this.type = "text";
});

document.getElementById("endDate").addEventListener("blur", function() {
	if (!this.value) this.type = "text";
});

function navigateToExecute() {
	window.location.href = 'execute.html';
}

function navigateToManage() {
	window.location.href = 'manage.html';
}

function showHome() {
	document.querySelector('.home-container').style.display = 'block';
	document.getElementById('helpSection').style.display = 'none';
	document.getElementById('resultsSection').style.display = 'none';
	setActiveNav(0);
}

function showResults() {
	document.querySelector('.home-container').style.display = 'none';
	document.getElementById('helpSection').style.display = 'none';
	document.getElementById('resultsSection').style.display = 'block';
	setActiveNav(1);
	loadResultsFiltered();
}

function showHelp() {
	document.querySelector('.home-container').style.display = 'none';
	document.getElementById('helpSection').style.display = 'block';
	document.getElementById('resultsSection').style.display = 'none';
	setActiveNav(2);
}

function setActiveNav(index) {
	const items = document.querySelectorAll('.nav-item');
	items.forEach((item, i) =>
		item.classList.toggle('active', i === index)
	);
}

function scrollToSection(sectionId) {
	const section = document.getElementById(sectionId);
	if (section) {
		section.scrollIntoView({ behavior: 'smooth', block: 'start' });

		document.querySelectorAll('.help-nav a').forEach(link => {
			link.classList.remove('active');
			if (link.getAttribute('href') === '#' + sectionId) {
				link.classList.add('active');
			}
		});
	}
	return false;
}

function copyUrl(button, url) {
	navigator.clipboard.writeText(url).then(() => {
		const originalText = button.textContent;
		button.textContent = 'Copied!';
		button.classList.add('copied');

		setTimeout(() => {
			button.textContent = originalText;
			button.classList.remove('copied');
		}, 2000);
	}).catch(err => {
		console.error('Failed to copy:', err);
	});
}
window.addEventListener("DOMContentLoaded", () => {
    handleRoute();
});

window.addEventListener("hashchange", handleRoute);
function handleRoute() {
    const hash = window.location.hash;

    if (hash.startsWith("#run/")) {
        const runId = hash.split("/")[1];
        showResults();
        openDashboardDirect(runId);
        return;
    }

    if (hash === "#result") {
        showResults();
        return;
    }

    if (hash === "#help") {
        showHelp();
        return;
    }

    if (hash.startsWith("#") && document.getElementById(hash.substring(1))) {
        showHelp();
        return;
    }
    showHome();
}

window.addEventListener('scroll', () => {
	if (document.getElementById('helpSection').style.display === 'block') {
		const sections = document.querySelectorAll('.content-block');
		let currentSection = '';

		sections.forEach(section => {
			const sectionTop = section.offsetTop;
			const sectionHeight = section.clientHeight;
			if (window.pageYOffset >= sectionTop - 150) {
				currentSection = section.getAttribute('id');
			}
		});

		if (currentSection) {
			document.querySelectorAll('.help-nav a').forEach(link => {
				link.classList.remove('active');
				if (link.getAttribute('href') === '#' + currentSection) {
					link.classList.add('active');
				}
			});
		}
	}
});

let sampleResults = [];

async function loadResults() {
	const response = await fetch("http://localhost:8080/api/results/runs");
	sampleResults = await response.json();
	renderResults(sampleResults);
}

function renderResults(results) {
	const tbody = document.getElementById("resultsBody");
	const noResults = document.getElementById("noResults");

	tbody.innerHTML = "";
	if (results.length === 0) {
		noResults.style.display = "block";
		return;
	}
	noResults.style.display = "none";

	results.forEach((result, index) => {
		const tr = document.createElement("tr");
		const P = result.passedExecutionsObserved || 0;
		const F = result.failedExecutionsObserved || 0;
		const S = result.skippedExecutionsObserved || 0;

		let pr;
		if (F === 0 && P > 0) {
			pr = "100.0";
		} else if (P + F > 0) {
			pr = ((P / (P + F)) * 100).toFixed(1);
		} else {
			pr = "0.0";
		}
		let status;
		if (F > 0) status = "FAIL";
		else if (P > 0) status = "PASS";
		else if (S > 0) status = "SKIP";
		else status = "SKIP";


		const rateClass = getPassRateClass(pr);

		tr.innerHTML = `
			<td><span class="expand-icon" id="icon-${index}">▶</span></td>
			<td><strong>${result.runId}</strong></td>
			<td>${result.startTime}</td>
			<td>${result.durationSec}</td>
			<td><span class="status-badge status-${status.toLowerCase()}">${status}</span></td>

			<td>
			  <span class="pass-rate ${rateClass}">
			    ${pr}%
			  </span>
			</td>
			<td>${result.totalSuites}</td>
			<td>${result.totalCookies}</td>
			<td>${result.passedTestcases}/${result.uniqueTestcases}</td>
			<td>
			  <button class="btn-action" onclick="downloadReport('${result.runId}')">Report</button>
			  <button class="btn-dashboard" onclick="openDashboard(event, '${result.runId}')">Dashboard</button>
			</td>
			`;

		tr.addEventListener("click", (e) => {
			if (e.target.closest(".btn-action")) return;
			if (e.target.closest(".btn-dashboard")) return;

			toggleDetail(index);
		});

		tr.querySelector(".expand-icon").addEventListener("click", (e) => {
			e.stopPropagation();
			toggleDetail(index);
		});


		tbody.appendChild(tr);

		const detailRow = document.createElement("tr");
		detailRow.id = `detail-${index}`;
		detailRow.className = "detail-row";
		detailRow.innerHTML = `
	            <td colspan="10" class="detail-cell">
	                <div class="detail-content">${generateDetailDashboard(result)}</div>
	            </td>
	        `;
		tbody.appendChild(detailRow);
	});
}


function generateDetailDashboard(result) {


	const P = result.passedExecutionsObserved || 0;
	const F = result.failedExecutionsObserved || 0;
	const S = result.skippedExecutionsObserved || 0;

	const TOTAL = P + F + S;

	let passPercent = TOTAL > 0 ? ((P / TOTAL) * 100).toFixed(1) : 0;
	let failPercent = TOTAL > 0 ? ((F / TOTAL) * 100).toFixed(1) : 0;
	let skippedPercent = TOTAL > 0 ? ((S / TOTAL) * 100).toFixed(1) : 0;

	const totalForPercent =
		result.passedExecutionsObserved +
		result.failedExecutionsObserved +
		result.skippedExecutionsObserved;
	return `
    	    <div style="padding: 10px 5px;">
    	    
    	      <!-- HEADER SUMMARY (top 4 cards) -->

				<div class="dashboard">
				  <div class="stat-card primary">
				    <div class="stat-label">TOTAL EXECUTIONS</div>
				    <div class="stat-value">${result.totalExecutionsObserved}</div>
				  </div>
				
				  <div class="stat-card success">
				    <div class="stat-label">PASSED EXECUTIONS</div>
				    <div class="stat-value">${result.passedExecutionsObserved}</div>
				  </div>
				
				  <div class="stat-card danger">
				    <div class="stat-label">FAILED EXECUTIONS</div>
				    <div class="stat-value">${result.failedExecutionsObserved}</div>
				  </div>
				
				  <div class="stat-card warning">
				    <div class="stat-label">SKIPPED EXECUTIONS</div>
				    <div class="stat-value">${result.skippedExecutionsObserved}</div>
				  </div>
				</div>

    	      <!-- BAR GRAPH -->
    	      <div class="progress-bar-container">
    	     <div class="progress-bar-bg">

				  <div class="progress-bar-passed"
				       style="width:${passPercent}%;">
				       ${passPercent > 0 ? passPercent + "%" : ""}
				  </div>
				
				  <div class="progress-bar-failed"
				       style="width:${failPercent}%;">
				       ${failPercent > 0 ? failPercent + "%" : ""}
				  </div>
				
				  <div class="progress-bar-skipped"
			     style="width:${skippedPercent}%;">
			     ${skippedPercent > 0 ? skippedPercent + "%" : ""}
			</div>

				
				</div>
	    	  </div>
		      <div class="time-info">
		        <strong>Started:</strong> ${result.startTime}  |  <strong>Ended:</strong> ${result.endTime}
		      </div>
    	    </div>
    	  `;
}

function openDashboard(e, runId) {
	e.stopPropagation();
	window.location.href = `http://localhost:8080/result.html?runId=${runId}`;
}

function toggleDetail(i) {
	const row = document.getElementById(`detail-${i}`);
	const icon = document.getElementById(`icon-${i}`);
	if (row.classList.contains("expanded")) {
		row.classList.remove("expanded");
		icon.textContent = "▶";
		return;
	}
	document.querySelectorAll(".detail-row").forEach(r => r.classList.remove("expanded"));
	document.querySelectorAll(".expand-icon").forEach(ic => ic.textContent = "▶");
	row.classList.add("expanded");
	icon.textContent = "▼";
}

function getPassRateClass(rate) {
	if (rate >= 70) return "high";
	if (rate >= 40) return "medium";
	return "low";
}

function calculatePassRate(passed, failed) {
	const total = passed + failed;
	if (total === 0) return 0;
	return ((passed / total) * 100).toFixed(1);
}

async function loadResultsFiltered() {
	const filter = document.getElementById("filterSelect").value;
	const startDate = document.getElementById("startDate").value;
	const endDate = document.getElementById("endDate").value;
	const sort = document.getElementById("sortSelect").value;
	const search = document.getElementById("searchInput").value.trim();

	const params = new URLSearchParams();

	if (filter) params.append("filter", filter);
	if (startDate) params.append("startDate", startDate);
	if (endDate) params.append("endDate", endDate);
	if (sort) params.append("sort", sort);
	if (search) params.append("search", search);

	params.append("limit", 200);
	params.append("offset", 0);

	const response = await fetch("http://localhost:8080/api/results/runs?" + params.toString());
	const data = await response.json();
	renderResults(data);
}

function downloadReport(id) {
	window.location.href = `http://localhost:8080/api/report/${id}`;
}

function refreshResults() {
	loadResultsFiltered();
}
document.getElementById("searchInput").addEventListener("input", () => {
	loadResultsFiltered();
});

document.addEventListener("DOMContentLoaded", () => {
    checkTestSuites();
});

function checkTestSuites() {
    fetch("http://localhost:8080/api/tests/suites")
        .then(response => {
            if (!response.ok) {
                throw new Error("Failed to fetch test suites");
            }
            return response.json();
        })
        .then(data => {
            const startBtn = document.getElementById("startTestingBtn");

            const noTests =
                (!data.tests || data.tests.length === 0) &&
                (!data.folders || Object.keys(data.folders).length === 0);

            if (noTests) {
                startBtn.disabled = true;
                startBtn.style.opacity = "0.5";
                startBtn.style.cursor = "not-allowed";
                startBtn.title = "Create at least one test suite before executing tests";
            } else {
                startBtn.disabled = false;
                startBtn.style.opacity = "1";
                startBtn.style.cursor = "pointer";
                startBtn.title = "";
            }
        })
        .catch(error => {
            console.error(error);
            const startBtn = document.getElementById("startTestingBtn");
            startBtn.disabled = true;
        }
    );
}
