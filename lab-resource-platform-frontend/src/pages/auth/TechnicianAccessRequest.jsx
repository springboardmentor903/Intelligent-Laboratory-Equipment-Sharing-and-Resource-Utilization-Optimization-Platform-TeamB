
import { useState } from "react";
import { Link } from "react-router-dom";
import api from "../../services/api";

export default function TechnicianAccessRequest() {
    const [form, setForm] = useState({
        fullName: "",
        email: "",
        institution: "",
        reason: "",
    });

    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState("");
    const [error, setError] = useState(false);

    function handleChange(event) {
        const { name, value } = event.target;

        setForm((previous) => ({
            ...previous,
            [name]: value,
        }));
    }

    async function handleSubmit(event) {
        event.preventDefault();
        setLoading(true);
        setMessage("");
        setError(false);

        try {
            const response = await api.post(
                "/api/technician-requests",
                form
            );

            setMessage(
                response.data?.message ||
                "Your request was submitted successfully. Please wait for administrator review."
            );

            setForm({
                fullName: "",
                email: "",
                institution: "",
                reason: "",
            });
        } catch (err) {
            setError(true);
            setMessage(
                err.response?.data?.message ||
                err.response?.data?.error ||
                "Could not submit the request. Check that the backend endpoint is implemented and running."
            );
        } finally {
            setLoading(false);
        }
    }

    return (
        <main className="technician-request-page">
            <form
                className="technician-request-form"
                onSubmit={handleSubmit}
            >
                <h1>Request Lab Technician Access</h1>

                <p>
                    Submit your details for review by the System Administrator.
                </p>

                {message && (
                    <div
                        className={error ? "request-error" : "request-success"}
                        role="status"
                    >
                        {message}
                    </div>
                )}

                <label htmlFor="fullName">Full Name</label>
                <input
                    id="fullName"
                    name="fullName"
                    value={form.fullName}
                    onChange={handleChange}
                    required
                    maxLength={100}
                    placeholder="Enter your full name"
                />

                <label htmlFor="email">Email Address</label>
                <input
                    id="email"
                    name="email"
                    type="email"
                    value={form.email}
                    onChange={handleChange}
                    required
                    maxLength={254}
                    placeholder="Enter your email"
                />

                <label htmlFor="institution">Institution</label>
                <input
                    id="institution"
                    name="institution"
                    value={form.institution}
                    onChange={handleChange}
                    required
                    maxLength={150}
                    placeholder="Enter your institution"
                />

                <label htmlFor="reason">Reason for Request</label>
                <textarea
                    id="reason"
                    name="reason"
                    value={form.reason}
                    onChange={handleChange}
                    required
                    maxLength={1000}
                    rows={4}
                    placeholder="Explain why you need technician access"
                />

                <button type="submit" disabled={loading}>
                    {loading ? "Submitting..." : "Submit Request"}
                </button>

                <p className="request-back-link">
                    Already have an account? <Link to="/login">Sign in</Link>
                </p>
            </form>
        </main>
    );
}
