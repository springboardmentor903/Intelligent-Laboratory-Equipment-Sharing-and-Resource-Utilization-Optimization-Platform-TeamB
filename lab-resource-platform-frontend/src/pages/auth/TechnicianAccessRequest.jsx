import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api from "../../services/api";

export default function TechnicianAccessRequest() {
    const [colleges, setColleges] = useState([]);
    const [selectedCollegeMode, setSelectedCollegeMode] = useState("");

    const [form, setForm] = useState({
        fullName: "",
        email: "",
        password: "",
        requestedRole: "LAB_TECHNICIAN",
        institution: "",
        department: "",
        reason: "",
    });

    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState("");
    const [error, setError] = useState(false);

    useEffect(() => {
        api.get("/api/colleges")
            .then((res) => {
                if (Array.isArray(res.data) && res.data.length > 0) {
                    setColleges(res.data);
                }
            })
            .catch(() => {});
    }, []);

    function handleChange(event) {
        const { name, value } = event.target;

        setForm((previous) => ({
            ...previous,
            [name]: value,
        }));
    }

    function handleCollegeSelect(event) {
        const val = event.target.value;
        setSelectedCollegeMode(val);

        if (val && val !== "OTHER") {
            setForm((prev) => ({
                ...prev,
                institution: val,
            }));
        } else if (val === "OTHER") {
            setForm((prev) => ({
                ...prev,
                institution: "",
            }));
        }
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
                "Your access request was submitted successfully with status PENDING. Once the System Administrator reviews and approves your request, you can log in directly with your email and password."
            );

            setForm({
                fullName: "",
                email: "",
                password: "",
                requestedRole: "LAB_TECHNICIAN",
                institution: "",
                department: "",
                reason: "",
            });
            setSelectedCollegeMode("");
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
                <h1>Request Role &amp; College Access</h1>

                <p>
                    Submit your access request and credentials for administrator verification. Once approved by the System Administrator, your account will be activated with college-specific permissions.
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
                    placeholder="Enter your official email"
                />

                <label htmlFor="password">Password</label>
                <input
                    id="password"
                    name="password"
                    type="password"
                    autoComplete="new-password"
                    value={form.password}
                    onChange={handleChange}
                    required
                    minLength={8}
                    maxLength={72}
                    placeholder="Create a password (minimum 8 characters)"
                />

                <label htmlFor="requestedRole">Choose Role</label>
                <select
                    id="requestedRole"
                    name="requestedRole"
                    value={form.requestedRole}
                    onChange={handleChange}
                    required
                >
                    <option value="INSTITUTION_ADMINISTRATOR">Institution Administrator</option>
                    <option value="DEPARTMENT_HEAD">Department Head</option>
                    <option value="LAB_MANAGER">Lab Manager</option>
                    <option value="LAB_TECHNICIAN">Lab Technician</option>
                    <option value="SYSTEM_ADMINISTRATOR">System Administrator</option>
                </select>

                {form.requestedRole !== "SYSTEM_ADMINISTRATOR" && (
                    <>
                        <label htmlFor="collegeSelect">Select College / Institution</label>
                        {colleges.length > 0 && (
                            <select
                                id="collegeSelect"
                                value={selectedCollegeMode}
                                onChange={handleCollegeSelect}
                                style={{ marginBottom: "12px" }}
                            >
                                <option value="">-- Choose from existing affiliated colleges --</option>
                                {colleges.map((c) => (
                                    <option key={c.id} value={c.name}>
                                        {c.name} ({c.code || "COL"})
                                    </option>
                                ))}
                                <option value="OTHER">+ Other (Enter New College / Institution)</option>
                            </select>
                        )}

                        {(colleges.length === 0 || selectedCollegeMode === "OTHER" || !selectedCollegeMode) && (
                            <input
                                id="institution"
                                name="institution"
                                value={form.institution}
                                onChange={handleChange}
                                required={form.requestedRole !== "SYSTEM_ADMINISTRATOR"}
                                maxLength={150}
                                placeholder="Enter institution or college name (e.g. CMRIT, CMRCET)"
                            />
                        )}
                    </>
                )}

                <label htmlFor="department">Department (Optional)</label>
                <input
                    id="department"
                    name="department"
                    value={form.department}
                    onChange={handleChange}
                    maxLength={150}
                    placeholder="e.g. Electrical Engineering, Computer Science, Biotechnology"
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
                    placeholder="Explain why you need this role and college laboratory access"
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
