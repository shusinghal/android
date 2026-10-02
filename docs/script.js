/**
 * Footage App Waitlist Handler with Google Sheets Webhook Integration
 * Developed by Shivayu Consultants
 */

// Paste your Google Apps Script Web App URL below after setup:
const GOOGLE_SHEET_WEBHOOK_URL = 'https://script.google.com/macros/s/AKfycbxi1NJcLmMcVgRSXGSobNoNa8N3jOd9NbS2VA2gTK8dlnVy78HbZ4eyMHteTj89n85p/exec'; // e.g., 'https://script.google.com/macros/s/AKfycbx.../exec'

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('interestForm');
    const emailInput = document.getElementById('userEmail');
    const formSuccess = document.getElementById('formSuccess');
    const liveCounter = document.getElementById('liveCounter');
    const submitBtn = form ? form.querySelector('button[type="submit"]') : null;

    // Load initial counter baseline
    const BASE_COUNT = 1482;
    let localSignups = parseInt(localStorage.getItem('footage_signup_count') || '0', 10);
    let currentTotal = BASE_COUNT + localSignups;

    if (liveCounter) {
        liveCounter.textContent = currentTotal.toLocaleString();
    }

    // Check if user on this device already joined
    const hasJoined = localStorage.getItem('footage_joined_waitlist');
    if (hasJoined && formSuccess && form) {
        form.classList.add('hidden');
        formSuccess.classList.remove('hidden');
    }

    if (form) {
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const email = emailInput ? emailInput.value.trim() : '';
            if (!email) return;

            // UI Loading state
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.style.opacity = '0.7';
            }

            const timestamp = new Date().toISOString();

            // 1. Send to Google Sheets Webhook (if URL provided)
            if (GOOGLE_SHEET_WEBHOOK_URL && GOOGLE_SHEET_WEBHOOK_URL.trim() !== '') {
                try {
                    await fetch(GOOGLE_SHEET_WEBHOOK_URL, {
                        method: 'POST',
                        mode: 'no-cors', // Bypasses CORS preflight for Google Apps Script Web Apps
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({
                            email: email,
                            timestamp: timestamp,
                            source: 'Footage Website Waitlist'
                        })
                    });
                } catch (err) {
                    console.warn('Google Sheets Webhook submission error:', err);
                }
            }

            // 2. Save locally in Browser LocalStorage (Backup & Local State)
            localSignups += 1;
            currentTotal += 1;
            localStorage.setItem('footage_signup_count', localSignups.toString());
            localStorage.setItem('footage_joined_waitlist', 'true');

            let storedEmails = JSON.parse(localStorage.getItem('footage_waitlist_emails') || '[]');
            storedEmails.push({ email: email, timestamp: timestamp });
            localStorage.setItem('footage_waitlist_emails', JSON.stringify(storedEmails));

            // 3. Update counter & reveal success message
            if (liveCounter) {
                liveCounter.textContent = currentTotal.toLocaleString();
            }

            form.classList.add('hidden');
            if (formSuccess) {
                formSuccess.classList.remove('hidden');
            }
        });
    }
});
