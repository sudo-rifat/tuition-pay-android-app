const currentYear = new Date().getFullYear();
const monthNames = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

// State
let students = [];
let payments = [];
let currentTab = 'viewSection'; // Default to Status Dashboard
let collectionMonthIdx = new Date().getMonth();
let dashboardMonthIdx = new Date().getMonth();
let collectionYear = new Date().getFullYear();
let dashboardYear = new Date().getFullYear();

// DOM Elements
const viewSection = document.getElementById('viewSection');
const updateSection = document.getElementById('updateSection');
const settingsSection = document.getElementById('settingsSection');
const viewListEl = document.getElementById('viewList');
const viewEmptyStateEl = document.getElementById('viewEmptyState');
const updateListEl = document.getElementById('updateList');
const loadingEl = document.getElementById('loading');
const settingsListEl = document.getElementById('settingsList');
const addStudentBtn = document.getElementById('addStudentBtn');
const studentModal = document.getElementById('studentModal');
const studentForm = document.getElementById('studentForm');
const cancelBtn = document.getElementById('cancelBtn');
const navBtns = document.querySelectorAll('.nav-btn');
const collectionMonthSelect = document.getElementById('collectionMonth');
const collectionYearSelect = document.getElementById('collectionYear');

// Security & Dashboard Elements
const securityOverlay = document.getElementById('securityOverlay');
const securityPinInput = document.getElementById('securityPin');
const unlockBtn = document.getElementById('unlockBtn'); // Updated
const dashboardStats = document.getElementById('dashboardStats');
const dashboardMonthSelect = document.getElementById('dashboardMonth');
const dashboardYearSelect = document.getElementById('dashboardYear');
const totalEarnedEl = document.getElementById('totalEarned');
const countPaidEl = document.getElementById('countPaid');
const countDueEl = document.getElementById('countDue');

// PIN Change Elements
const changePinBtn = document.getElementById('changePinBtn');
const pinModal = document.getElementById('pinModal');
const pinForm = document.getElementById('pinForm');
const oldPinInput = document.getElementById('oldPin');
const newPinInput = document.getElementById('newPin');
const cancelPinBtn = document.getElementById('cancelPinBtn');

// Init
async function init() {
    try {
        checkActivation(); // STEP 1: Check License
        initPassword(); // Ensure Password exists
        checkAuth();
        setupSecurity();
        setupPasswordChange(); // New Feature
        setupHelp(); // User Manual
        setupTabs();
        setupDateSelectors();
        await loadData();
        await runMigration(); // Fix old payments without dates
        render();
        setupReports(); // PDF Report System
    } catch (err) {
        console.error('Init error:', err);
    } finally {
        loadingEl.classList.add('hidden');
    }
}

// ==========================================
// ACTIVATION SYSTEM (Device Lock)
// Secret Formula: (DeviceID * 3) + 77
// ==========================================
function checkActivation() {
    const activationOverlay = document.getElementById('activationOverlay');
    const deviceIdDisplay = document.getElementById('deviceIdDisplay');
    const activationCodeInput = document.getElementById('activationCode');
    const activateBtn = document.getElementById('activateBtn');
    const activationError = document.getElementById('activationError');

    // 1. Check if already activated
    if (localStorage.getItem('app_activated') === 'true') {
        activationOverlay.style.display = 'none';
        return;
    }

    // 2. Generate or Get Device ID (Persistent random 4-digit)
    let deviceId = localStorage.getItem('device_id');
    if (!deviceId) {
        deviceId = Math.floor(1000 + Math.random() * 9000); // 1000-9999
        localStorage.setItem('device_id', deviceId);
    }
    deviceIdDisplay.textContent = deviceId;
    activationOverlay.classList.remove('hidden'); // Show lock screen

    // 3. Validation Logic
    function validateLicense() {
        const inputCode = parseInt(activationCodeInput.value);
        const secretCode = (parseInt(deviceId) * 3) + 77; // THE SECRET FORMULA

        if (inputCode === secretCode) {
            localStorage.setItem('app_activated', 'true');
            activationOverlay.style.display = 'none';
            alert('App Activated Successfully! Welcome.');
            location.reload();
        } else {
            activationError.style.display = 'block';
            activationCodeInput.value = '';
        }
    }

    activateBtn.addEventListener('click', validateLicense);
}

// --- Security Logic ---
function initPassword() {
    // Migrate old PIN if exists, otherwise default
    if (!localStorage.getItem('app_password')) {
        const oldPin = localStorage.getItem('app_pin');
        if (oldPin) {
            localStorage.setItem('app_password', oldPin); // seamless transition
        } else {
            localStorage.setItem('app_password', 'admin'); // Default Password
        }
    }
}

function getPassword() {
    return localStorage.getItem('app_password') || 'admin';
}

function checkAuth() {
    const isAuth = sessionStorage.getItem('isAuthenticated');
    if (isAuth === 'true') {
        securityOverlay.classList.add('hidden');
    } else {
        securityOverlay.classList.remove('hidden');
    }
}

function setupSecurity() {
    function unlock() {
        if (securityPinInput.value === getPassword()) {
            sessionStorage.setItem('isAuthenticated', 'true');
            securityOverlay.classList.add('hidden');
            securityPinInput.value = ''; // Reset input
        } else {
            alert('Incorrect Password');
            securityPinInput.value = '';
        }
    }

    unlockBtn.addEventListener('click', unlock);
    securityPinInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') unlock();
    });

    // Show Password Toggle for Login
    const showLoginPin = document.getElementById('showLoginPin');
    if (showLoginPin) {
        showLoginPin.addEventListener('change', () => {
            securityPinInput.type = showLoginPin.checked ? 'text' : 'password';
        });
    }
}

function setupPasswordChange() {
    changePinBtn.addEventListener('click', () => {
        pinForm.reset();
        pinModal.classList.remove('hidden');
    });

    cancelPinBtn.addEventListener('click', () => {
        pinModal.classList.add('hidden');
    });

    pinForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const oldPass = oldPinInput.value;
        const newPass = newPinInput.value;

        if (oldPass !== getPassword()) {
            alert('Current password is incorrect.');
            return;
        }

        if (newPass.length < 4) {
             alert('Password must be at least 4 characters long.');
             return;
        }

        localStorage.setItem('app_password', newPass);
        alert('Password updated successfully!');
        pinModal.classList.add('hidden');
    });

    // Show Password Toggle for Change Password Modal
    const showChangePin = document.getElementById('showChangePin');
    if (showChangePin) {
        showChangePin.addEventListener('change', () => {
            const type = showChangePin.checked ? 'text' : 'password';
            oldPinInput.type = type;
            newPinInput.type = type;
        });
    }
}

async function loadData() {
    const [allStudents, allPayments] = await Promise.all([
        db.getStudents(),
        db.getPayments()
    ]);
    students = allStudents;
    payments = allPayments;
}

// Migration: Run once to fix old payments without date
let migrationDone = false;
async function runMigration() {
    if (migrationDone) return;
    migrationDone = true;

    for (const payment of payments) {
        if (!payment.date) {
            payment.date = new Date().toISOString();
            await db.addPayment(payment);
        }
    }
    // Reload after migration
    await loadData();
}

// --- Navigation & Setup ---
function setupTabs() {
    navBtns.forEach(btn => {
        btn.addEventListener('click', (e) => {
            const targetId = btn.dataset.target;
            switchTab(targetId);
        });
    });
}

function switchTab(tabId) {
    currentTab = tabId;

    // UI Updates
    navBtns.forEach(btn => {
        if (btn.dataset.target === tabId) btn.classList.add('active');
        else btn.classList.remove('active');
    });

    viewSection.classList.add('hidden');
    updateSection.classList.add('hidden');
    settingsSection.classList.add('hidden');

    document.getElementById(tabId).classList.remove('hidden');

    render();
}

function setupDateSelectors() {
    // yearDisplay.textContent = currentYear; // Removed

    // Generate Years (Current - 1 to Current + 5)
    // We want a range around the current year
    const baseYear = new Date().getFullYear();
    const yearOptions = [];
    for (let i = baseYear - 1; i <= baseYear + 5; i++) {
        yearOptions.push(i);
    }

    const yearOptionsHtml = yearOptions.map(y => `<option value="${y}">${y}</option>`).join('');

    // --- Collection Selectors (Update Tab) ---
    // Month
    collectionMonthSelect.innerHTML = monthNames.map((m, i) =>
        `<option value="${i}" ${i === collectionMonthIdx ? 'selected' : ''}>${m}</option>`
    ).join('');

    // Year
    collectionYearSelect.innerHTML = yearOptionsHtml;
    collectionYearSelect.value = collectionYear;

    // Listeners
    collectionMonthSelect.addEventListener('change', (e) => {
        collectionMonthIdx = parseInt(e.target.value);
        render();
    });
    collectionYearSelect.addEventListener('change', (e) => {
        collectionYear = parseInt(e.target.value);
        render();
    });

    // --- Dashboard Selectors (Status Tab) ---
    // Month
    dashboardMonthSelect.innerHTML = monthNames.map((m, i) =>
        `<option value="${i}" ${i === dashboardMonthIdx ? 'selected' : ''}>${m}</option>`
    ).join('');

    // Year
    dashboardYearSelect.innerHTML = yearOptionsHtml;
    dashboardYearSelect.value = dashboardYear;

    // Listeners
    dashboardMonthSelect.addEventListener('change', (e) => {
        dashboardMonthIdx = parseInt(e.target.value);
        renderViewSection();
    });
    dashboardYearSelect.addEventListener('change', (e) => {
        dashboardYear = parseInt(e.target.value);
        renderViewSection();
    });
}

// --- Rendering ---
function render() {
    if (students.length === 0) {
        viewListEl.classList.add('hidden');
        dashboardStats.classList.add('hidden');
        viewEmptyStateEl.classList.remove('hidden');
        updateListEl.innerHTML = '<div class="empty-state">No students found.</div>';
    } else {
        viewEmptyStateEl.classList.add('hidden');
    }

    if (currentTab === 'viewSection') {
        renderViewSection();
    } else if (currentTab === 'updateSection') {
        renderUpdateSection();
    } else if (currentTab === 'settingsSection') {
        renderSettingsSection();
    }
}

// VIEW SECTION: Status Dashboard + Analytics
function renderViewSection() {
    viewListEl.classList.remove('hidden');
    dashboardStats.classList.remove('hidden');
    viewListEl.innerHTML = '';

    // Use SELECTED Dashboard Year/Month
    const targetBaseYear = dashboardYear;

    // 1. Calculate Dashboard Stats for SELECTED Dashboard Month/Year
    let totalEarned = 0;
    let paidCount = 0;
    let dueCount = 0;

    students.forEach(student => {
        // Check if Paid for Dashboard Month
        const targetMonth = getTargetMonthIndex(student.payment_type, dashboardMonthIdx);
        const targetYear = getTargetYear(student.payment_type, dashboardMonthIdx, targetBaseYear);

        const isPaid = payments.some(p =>
            p.student_id === student.id &&
            p.month_paid_for === targetMonth &&
            p.year === targetYear
        );

        if (isPaid) {
            paidCount++;
            totalEarned += (student.monthly_fee || 0); // Add fee if paid
        } else {
            dueCount++;
        }
    });

    // Update Dashboard UI
    totalEarnedEl.textContent = `৳${totalEarned}`;
    countPaidEl.textContent = paidCount;
    countDueEl.textContent = dueCount;


    // 2. Render Student Status Cards
    students.forEach(student => {
        const card = document.createElement('div');
        card.className = 'student-card';

        const typeLabel = student.payment_type === 'A' ? 'Current Month' : 'Previous Month';
        const feeLabel = student.monthly_fee ? `৳${student.monthly_fee}` : 'No Fee';

        let statusGridHtml = '';
        monthNames.forEach((name, index) => {
            const isPaid = payments.some(p =>
                p.student_id === student.id &&
                p.month_paid_for === index &&
                p.year === targetBaseYear
            );

            let statusClass = 'status-future';
            if (isPaid) {
                statusClass = 'status-paid';
            } else {
                // Due Logic (based on Real Time vs Selected Time)
                // If viewing a Past Year -> All unpaid are DUE (Red)
                // If viewing Current Year -> Past months are DUE
                // If viewing Future Year -> All are Future (Grey)

                const now = new Date();
                const realCurrentYear = now.getFullYear();
                const realCurrentMonth = now.getMonth();

                if (targetBaseYear < realCurrentYear) {
                    statusClass = 'status-due';
                } else if (targetBaseYear === realCurrentYear) {
                    if (index <= realCurrentMonth) {
                        statusClass = 'status-due';
                    }
                }
            }


            statusGridHtml += `
            <div class="status-item">
                <div class="status-dot ${statusClass}">${name}</div>
            </div>`;
        });

        card.innerHTML = `
            <div class="student-header">
                <div class="student-info">
                    <h3>${student.name}</h3>
                    <div class="student-type">Type ${student.payment_type} • ${feeLabel}</div>
                </div>
            </div>
            <div class="status-grid">
                ${statusGridHtml}
            </div>
        `;
        viewListEl.appendChild(card);
    });
}

// UPDATE SECTION: Collection ACTION
function renderUpdateSection() {
    updateListEl.innerHTML = '';

    students.forEach(student => {
        const card = document.createElement('div');
        card.className = 'student-card';
        card.style.padding = '0.75rem';

        const targetMonth = getTargetMonthIndex(student.payment_type, collectionMonthIdx);
        const targetYear = getTargetYear(student.payment_type, collectionMonthIdx, collectionYear);

        const isPaid = payments.some(p =>
            p.student_id === student.id &&
            p.month_paid_for === targetMonth &&
            p.year === targetYear
        );

        let paymentDesc = '';
        if (student.payment_type === 'A') {
             const yearStr = collectionYear !== currentYear ? ` ${collectionYear}` : '';
            paymentDesc = `Paying for <b>${monthNames[collectionMonthIdx]}${yearStr}</b>`;
        } else {
             const targetMonthName = monthNames[targetMonth];
             const targetYearStr = targetYear !== currentYear ? ` ${targetYear}` : '';
             paymentDesc = `paying for <b>${targetMonthName}${targetYearStr}</b>`;
        }

        // Fee Label for Update
        const feeDisplay = student.monthly_fee ? `<span style="font-weight:600; color:var(--text-main)">৳${student.monthly_fee}</span>` : '';

        card.innerHTML = `
            <div class="update-row">
                <div class="student-info">
                    <h3>${student.name}</h3>
                    <div class="student-type">${paymentDesc}</div>
                </div>
                <div style="display:flex; align-items:center; gap:0.75rem;">
                    ${feeDisplay}
                    <label class="month-checkbox" style="flex-direction:row;width:auto;">
                        <input type="checkbox"
                            ${isPaid ? 'checked' : ''}
                            onchange="togglePayment(${student.id}, this.checked)"
                            style="display:block; width:24px; height:24px; accent-color:var(--primary-color);"
                        >
                    </label>
                </div>
            </div>
        `;

        updateListEl.appendChild(card);
    });
}

function renderSettingsSection() {
    settingsListEl.innerHTML = '';

    if (students.length === 0) {
        settingsListEl.innerHTML = '<div class="empty-state">No students. Add one above.</div>';
    }

    students.forEach(student => {
        const card = document.createElement('div');
        card.className = 'student-card';

        card.innerHTML = `
            <div class="student-header" style="margin-bottom:0">
                <div class="student-info">
                    <h3>${student.name}</h3>
                    <div class="student-type">Type ${student.payment_type} • ৳${student.monthly_fee || 0}</div>
                </div>
                <div class="action-group">
                   <button class="edit-btn" onclick="openEditModal(${student.id})">✎</button>
                   <button class="delete-btn" onclick="deleteStudent(${student.id})">🗑️</button>
                </div>
            </div>
        `;
        settingsListEl.appendChild(card);
    });
}

// --- Logic Helpers ---
function getTargetMonthIndex(type, clickedIndex) {
    if (type === 'A') return clickedIndex;
    let idx = clickedIndex - 1;
    if (idx < 0) idx = 11;
    return idx;
}

function getTargetYear(type, clickedIndex, currentYr) {
    if (type === 'A') return currentYr;
    if (clickedIndex === 0) return currentYr - 1;
    return currentYr;
}

// --- Action Handlers ---
window.togglePayment = async (studentId, isChecked) => {
    const student = students.find(s => s.id === studentId);
    if (!student) return;

    const targetMonth = getTargetMonthIndex(student.payment_type, collectionMonthIdx);
    const targetYear = getTargetYear(student.payment_type, collectionMonthIdx, collectionYear);

    try {
        if (isChecked) {
            await db.addPayment({
                student_id: studentId,
                year: targetYear,
                month_paid_for: targetMonth,
                paid_on_month: collectionMonthIdx,
                date: new Date().toISOString() // For PDF reports
            });
        } else {
            await db.removePayment(studentId, targetYear, targetMonth);
        }
        await loadData();
        render();
    } catch (err) {
        console.error('Payment toggle error:', err);
        alert('Failed to update payment');
    }
};


// --- Student CRUD ---
function openModal(isEdit = false) {
    studentModal.classList.remove('hidden');
    document.getElementById('modalTitle').textContent = isEdit ? 'Edit Student' : 'Add Student';
}

window.openEditModal = (id) => {
    const student = students.find(s => s.id === id);
    if (!student) return;
    document.getElementById('studentId').value = student.id;
    document.getElementById('studentName').value = student.name;
    document.getElementById('studentFee').value = student.monthly_fee || 0;
    const radio = document.querySelector(`input[name="paymentType"][value="${student.payment_type}"]`);
    if (radio) radio.checked = true;
    openModal(true);
};

addStudentBtn.addEventListener('click', () => {
    studentForm.reset();
    document.getElementById('studentId').value = '';
    document.getElementById('studentFee').value = '';
    openModal(false);
});

cancelBtn.addEventListener('click', () => {
    studentModal.classList.add('hidden');
});

studentForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const idInput = document.getElementById('studentId').value;
    const name = document.getElementById('studentName').value;
    const fee = parseInt(document.getElementById('studentFee').value) || 0;
    const type = document.querySelector('input[name="paymentType"]:checked').value;

    try {
        const studentData = { name, payment_type: type, monthly_fee: fee };

        if (idInput) {
            studentData.id = parseInt(idInput);
            await db.updateStudent(studentData);
        } else {
            await db.addStudent(studentData);
        }
        studentModal.classList.add('hidden');
        await init();
    } catch (err) {
        console.error('Save error:', err);
        alert('Failed to save');
    }
});

window.deleteStudent = async (id) => {
    if (confirm('Delete student?')) {
        await db.deleteStudent(id);
        await init();
    }
};

// --- User Manual Logic ---
function setupHelp() {
    const helpBtn = document.getElementById('helpBtn');
    const helpModal = document.getElementById('helpModal');
    const closeHelpBtn = document.getElementById('closeHelpBtn');

    if (helpBtn && helpModal && closeHelpBtn) {
        helpBtn.addEventListener('click', () => {
            helpModal.classList.remove('hidden');
        });

        closeHelpBtn.addEventListener('click', () => {
            helpModal.classList.add('hidden');
        });
    }
}

// ==========================================
// PDF Report Generation System
// ==========================================
const { jsPDF } = window.jspdf;

function setupReports() {
    const reportTypeSelect = document.getElementById('reportType');
    const studentSelectGroup = document.getElementById('studentSelectGroup');
    const monthSelectGroup = document.getElementById('monthSelectGroup');
    const reportStudentSelect = document.getElementById('reportStudent');
    const reportYearSelect = document.getElementById('reportYear');
    const reportMonthSelect = document.getElementById('reportMonth');
    const viewReportBtn = document.getElementById('viewReportBtn');
    const downloadReportBtn = document.getElementById('downloadReportBtn');

    // Populate year dropdown
    const baseYear = new Date().getFullYear();
    for (let i = baseYear - 2; i <= baseYear + 1; i++) {
        reportYearSelect.innerHTML += `<option value="${i}">${i}</option>`;
    }
    reportYearSelect.value = baseYear;

    // Populate month dropdown
    monthNames.forEach((m, i) => {
        reportMonthSelect.innerHTML += `<option value="${i}">${m}</option>`;
    });
    reportMonthSelect.value = new Date().getMonth();

    // Toggle visibility based on report type
    function updateVisibility() {
        const type = reportTypeSelect.value;
        studentSelectGroup.style.display = type === 'student' ? 'block' : 'none';
        monthSelectGroup.style.display = type === 'monthly' ? 'block' : 'none';
    }
    reportTypeSelect.addEventListener('change', updateVisibility);
    updateVisibility();

    // Populate students dropdown
    function populateStudents() {
        reportStudentSelect.innerHTML = students.map(s =>
            `<option value="${s.id}">${s.name}</option>`
        ).join('');
    }
    populateStudents();

    // Generate Report
    viewReportBtn.addEventListener('click', () => generateReport(false));
    downloadReportBtn.addEventListener('click', () => generateReport(true));

    function generateReport(download) {
        const type = reportTypeSelect.value;
        const year = parseInt(reportYearSelect.value);
        const month = parseInt(reportMonthSelect.value);
        const studentId = parseInt(reportStudentSelect.value);

        let doc;
        if (type === 'student') {
            doc = generateStudentReport(studentId, year);
        } else if (type === 'monthly') {
            doc = generateMonthlyReport(year, month);
        } else {
            doc = generateYearlyReport(year);
        }

        if (download) {
            doc.save(`report_${type}_${Date.now()}.pdf`);
        } else {
            window.open(doc.output('bloburl'), '_blank');
        }
    }
}

function generateStudentReport(studentId, year) {
    const student = students.find(s => s.id === studentId);
    const studentPayments = payments.filter(p => p.student_id === studentId);

    const doc = new jsPDF();

    // Header
    doc.setFillColor(79, 70, 229); // Indigo
    doc.rect(0, 0, 210, 40, 'F');
    doc.setTextColor(255, 255, 255);
    doc.setFontSize(22);
    doc.text('Student Payment Report', 105, 20, { align: 'center' });
    doc.setFontSize(12);
    doc.text(`Generated: ${new Date().toLocaleDateString('en-GB')}`, 105, 32, { align: 'center' });

    // Student Info
    doc.setTextColor(0, 0, 0);
    doc.setFontSize(16);
    doc.text(`Student: ${student?.name || 'Unknown'}`, 14, 55);
    doc.setFontSize(11);
    doc.text(`Monthly Fee: Tk ${student?.monthly_fee || 0}`, 14, 63);
    doc.text(`Payment Type: ${student?.payment_type === 'A' ? 'Current Month' : 'Next Month'}`, 14, 70);

    // Payment Table
    const tableData = studentPayments.map(p => [
        monthNames[p.month_paid_for],
        p.year,
        p.date ? new Date(p.date).toLocaleDateString('en-GB') : 'N/A',
        `Tk ${student?.monthly_fee || 0}`
    ]);

    doc.autoTable({
        startY: 80,
        head: [['Month Paid For', 'Year', 'Payment Date', 'Amount']],
        body: tableData.length > 0 ? tableData : [['No payments found', '', '', '']],
        theme: 'striped',
        headStyles: { fillColor: [79, 70, 229] },
        styles: { halign: 'center' }
    });

    // Summary
    const finalY = doc.lastAutoTable.finalY + 10;
    const totalAmount = studentPayments.length * (student?.monthly_fee || 0);
    doc.setFontSize(12);
    doc.setFont(undefined, 'bold');
    doc.text(`Total Payments: ${studentPayments.length}`, 14, finalY);
    doc.text(`Total Amount: Tk ${totalAmount.toLocaleString()}`, 14, finalY + 8);

    return doc;
}

function generateMonthlyReport(year, month) {
    const monthPayments = payments.filter(p => p.year === year && p.month_paid_for === month);

    const doc = new jsPDF();

    // Header
    doc.setFillColor(16, 185, 129); // Green
    doc.rect(0, 0, 210, 40, 'F');
    doc.setTextColor(255, 255, 255);
    doc.setFontSize(22);
    doc.text('Monthly Payment Report', 105, 20, { align: 'center' });
    doc.setFontSize(12);
    doc.text(`${monthNames[month]} ${year}`, 105, 32, { align: 'center' });

    // Table
    const tableData = monthPayments.map(p => {
        const student = students.find(s => s.id === p.student_id);
        return [
            student?.name || 'Unknown',
            p.date ? new Date(p.date).toLocaleDateString('en-GB') : 'N/A',
            `Tk ${student?.monthly_fee || 0}`
        ];
    });

    doc.autoTable({
        startY: 50,
        head: [['Student Name', 'Payment Date', 'Amount']],
        body: tableData.length > 0 ? tableData : [['No payments for this month', '', '']],
        theme: 'striped',
        headStyles: { fillColor: [16, 185, 129] },
        styles: { halign: 'center' }
    });

    // Summary
    const finalY = doc.lastAutoTable.finalY + 10;
    const totalAmount = monthPayments.reduce((sum, p) => {
        const student = students.find(s => s.id === p.student_id);
        return sum + (student?.monthly_fee || 0);
    }, 0);
    doc.setTextColor(0, 0, 0);
    doc.setFontSize(12);
    doc.setFont(undefined, 'bold');
    doc.text(`Total Students Paid: ${monthPayments.length}`, 14, finalY);
    doc.text(`Total Collected: Tk ${totalAmount.toLocaleString()}`, 14, finalY + 8);

    return doc;
}

function generateYearlyReport(year) {
    const yearPayments = payments.filter(p => p.year === year);

    const doc = new jsPDF();

    // Header
    doc.setFillColor(239, 68, 68); // Red
    doc.rect(0, 0, 210, 40, 'F');
    doc.setTextColor(255, 255, 255);
    doc.setFontSize(22);
    doc.text('Yearly Summary Report', 105, 20, { align: 'center' });
    doc.setFontSize(12);
    doc.text(`Year: ${year}`, 105, 32, { align: 'center' });

    // Monthly Breakdown Table
    const monthlyData = monthNames.map((name, i) => {
        const monthPayments = yearPayments.filter(p => p.month_paid_for === i);
        const monthTotal = monthPayments.reduce((sum, p) => {
            const student = students.find(s => s.id === p.student_id);
            return sum + (student?.monthly_fee || 0);
        }, 0);
        return [name, monthPayments.length, `Tk ${monthTotal.toLocaleString()}`];
    });

    doc.autoTable({
        startY: 50,
        head: [['Month', 'Payments Count', 'Amount Collected']],
        body: monthlyData,
        theme: 'striped',
        headStyles: { fillColor: [239, 68, 68] },
        styles: { halign: 'center' }
    });

    // Grand Total
    const finalY = doc.lastAutoTable.finalY + 10;
    const grandTotal = yearPayments.reduce((sum, p) => {
        const student = students.find(s => s.id === p.student_id);
        return sum + (student?.monthly_fee || 0);
    }, 0);
    doc.setTextColor(0, 0, 0);
    doc.setFontSize(14);
    doc.setFont(undefined, 'bold');
    doc.text(`Grand Total for ${year}: Tk ${grandTotal.toLocaleString()}`, 14, finalY);
    doc.text(`Total Payment Records: ${yearPayments.length}`, 14, finalY + 10);

    return doc;
}

// Start
init();
