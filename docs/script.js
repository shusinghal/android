document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('interestForm');
    const emailInput = document.getElementById('userEmail');
    const formSuccess = document.getElementById('formSuccess');
    const liveCounter = document.getElementById('liveCounter');

    // Load initial counter from LocalStorage or default baseline
    const BASE_COUNT = 1482;
    let localSignups = parseInt(localStorage.getItem('footage_signup_count') || '0', 10);
    let currentTotal = BASE_COUNT + localSignups;

    if (liveCounter) {
        liveCounter.textContent = currentTotal.toLocaleString();
    }

    // Check if user has already joined
    const hasJoined = localStorage.getItem('footage_joined_waitlist');
    if (hasJoined && formSuccess && form) {
        form.classList.add('hidden');
        formSuccess.classList.remove('hidden');
    }

    if (form) {
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            const email = emailInput.value.trim();
            if (!email) return;

            // Increment tracker
            localSignups += 1;
            currentTotal += 1;
            localStorage.setItem('footage_signup_count', localSignups.toString());
            localStorage.setItem('footage_joined_waitlist', 'true');
            
            // Store signed-up emails locally for tracking
            let storedEmails = JSON.parse(localStorage.getItem('footage_waitlist_emails') || '[]');
            storedEmails.push({ email: email, timestamp: new Date().toISOString() });
            localStorage.setItem('footage_waitlist_emails', JSON.stringify(storedEmails));

            // Animate counter
            if (liveCounter) {
                liveCounter.textContent = currentTotal.toLocaleString();
            }

            // Optional: Formspree / Webhook Submission
            // fetch('https://formspree.io/f/YOUR_FORM_ID', {
            //     method: 'POST',
            //     headers: { 'Content-Type': 'application/json' },
            //     body: JSON.stringify({ email: email, date: new Date().toISOString() })
            // });

            // Display Success State
            form.classList.add('hidden');
            formSuccess.classList.remove('hidden');
        });
    }
});
