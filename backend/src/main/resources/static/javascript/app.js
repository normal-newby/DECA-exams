// Practice-exam UI: pick a cluster/exam/areas, answer questions, see the key's explanation, track score by area.
import { initUpload } from "./upload.js";

const DEFAULT_CLUSTERS = ["Finance", "Marketing", "Principles"];
const LETTERS = ["A", "B", "C", "D"];

const $ = (id) => document.getElementById(id);
const el = (tag, props = {}, ...children) => {
    const node = Object.assign(document.createElement(tag), props);
    node.append(...children);
    return node;
};
const displayName = (examName) => examName.replace(/\.pdf$/i, "");

const state = {
    cluster: null,
    selectedIAs: new Set(),
    questions: [],
    picks: [],      // picks[i] = letter the user chose for question i, or undefined
    index: 0,
};

const store = {
    get(key) { try { return localStorage.getItem(key); } catch { return null; } },
    set(key, value) { try { localStorage.setItem(key, value); } catch { /* storage unavailable */ } },
};

async function api(path, options) {
    const res = await fetch(`/api/${path}`, options);
    const body = await res.json().catch(() => null);
    if (!res.ok) throw new Error(body?.error || `The server returned ${res.status}.`);
    return body;
}

function setStatus(text, isError = false) {
    const status = $("setupStatus");
    status.textContent = text;
    status.classList.toggle("is-error", isError);
}

/* ---------- Clusters, exams, areas ---------- */

export async function loadClusters(selected) {
    let fromDb = [];
    try { fromDb = await api("clusters"); } catch (e) { setStatus(e.message, true); }
    const clusters = [...new Set([...DEFAULT_CLUSTERS, ...fromDb])];
    const nav = document.querySelector(".clusters");
    nav.replaceChildren(...clusters.map((name) =>
        el("button", { type: "button", className: "cluster-tab", textContent: name, onclick: () => selectCluster(name) })));
    const uploadSelect = $("uploadCluster");
    uploadSelect.replaceChildren(el("option", { value: "", textContent: "Detect from PDF" }),
        ...clusters.map((name) => el("option", { value: name, textContent: name })));
    const initial = selected || state.cluster || store.get("cluster");
    await selectCluster(clusters.includes(initial) ? initial : clusters[0]);
}

async function selectCluster(name) {
    state.cluster = name;
    store.set("cluster", name);
    document.body.dataset.cluster = name;
    for (const tab of document.querySelectorAll(".cluster-tab")) {
        tab.setAttribute("aria-pressed", String(tab.textContent === name));
    }
    state.selectedIAs.clear();
    setStatus("");
    const [exams, ias] = await Promise.all([
        api(`getExams/${encodeURIComponent(name)}`).catch(() => []),
        api(`${encodeURIComponent(name)}/getIAs/`).catch(() => []),
    ]);

    const examSelect = $("exam");
    examSelect.replaceChildren(
        el("option", { value: "all", textContent: exams.length ? `All ${name} exams (${exams.length})` : "No exams yet" }),
        ...exams.map((exam) => el("option", { value: exam, textContent: displayName(exam) })));
    examSelect.disabled = exams.length === 0;
    $("start").disabled = exams.length === 0;
    $("shuffle").checked = true;
    if (!exams.length) setStatus(`There are no ${name} exams yet. Use "Add an exam" to upload one.`);

    $("ias").replaceChildren(...ias.filter(Boolean).sort().map((ia) =>
        el("button", { type: "button", className: "chip", textContent: ia, ariaPressed: "false", onclick: (e) => toggleIA(ia, e.currentTarget) })));
    updateIAHint();
}

function toggleIA(ia, chip) {
    if (state.selectedIAs.has(ia)) state.selectedIAs.delete(ia);
    else state.selectedIAs.add(ia);
    chip.setAttribute("aria-pressed", String(state.selectedIAs.has(ia)));
    updateIAHint();
}

function updateIAHint() {
    const n = state.selectedIAs.size;
    $("iaHint").textContent = n === 0 ? "All areas. Tap an area to focus on it." : `${n} area${n > 1 ? "s" : ""} selected`;
}

/* ---------- Starting a round ---------- */

async function startRound(e) {
    e.preventDefault();
    const exam = $("exam").value;
    const ias = state.selectedIAs.size ? [...state.selectedIAs] : ["all"];
    setStatus("Loading questions…");
    let questions;
    try {
        questions = await api(`getExams/${encodeURIComponent(state.cluster)}/${encodeURIComponent(exam)}/questions`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ ias }),
        });
    } catch (err) {
        setStatus(err.message, true);
        return;
    }
    if (!questions?.length) {
        setStatus("No questions match that exam and those areas. Try selecting more areas.", true);
        return;
    }
    if ($("shuffle").checked) {
        for (let i = questions.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [questions[i], questions[j]] = [questions[j], questions[i]];
        }
    }
    const count = Number($("count").value);
    state.questions = count ? questions.slice(0, count) : questions;
    state.picks = new Array(state.questions.length);
    state.index = 0;
    setStatus(`${state.questions.length} question${state.questions.length > 1 ? "s" : ""} ready.`);

    $("empty").hidden = true;
    $("sheet").hidden = false;
    $("strip").replaceChildren(...state.questions.map((_, i) =>
        el("li", {}, el("button", { type: "button", ariaLabel: `Question ${i + 1}`, onclick: () => go(i) }))));
    render();
    renderScore();
    if (window.matchMedia("(max-width: 820px)").matches) $("sheet").scrollIntoView({ behavior: "smooth" });
}

/* ---------- Question sheet ---------- */

function go(i) {
    if (i < 0 || i >= state.questions.length) return;
    state.index = i;
    render();
}

function render() {
    const i = state.index;
    const q = state.questions[i];
    const pick = state.picks[i];
    const answered = pick !== undefined;

    $("progress").replaceChildren(`Question ${i + 1} `, el("span", { textContent: `of ${state.questions.length}` }));
    $("source").textContent = q.examName ? displayName(q.examName) : "";
    $("question").textContent = q.question;

    const answers = [q.answerA, q.answerB, q.answerC, q.answerD];
    $("options").replaceChildren(...LETTERS.map((letter, n) => {
        const button = el("button", { type: "button", className: "option", disabled: answered, onclick: () => answer(letter) },
            el("span", { className: "bubble", ariaHidden: "true" }, el("span", { className: "bubble-letter", textContent: letter })),
            el("span", { textContent: answers[n] || "(blank in the PDF)" }));
        button.setAttribute("aria-label", `${letter}: ${answers[n]}`);
        if (answered) {
            const isAnswer = letter === q.correctAnswer;
            button.classList.toggle("is-answer", isAnswer);
            button.classList.toggle("is-picked", letter === pick);
            button.classList.toggle("is-wrong", letter === pick && !isAnswer);
            button.classList.toggle("is-dim", !isAnswer && letter !== pick);
        }
        return el("li", {}, button);
    }));

    const note = $("note");
    note.hidden = !answered;
    if (answered) {
        const right = pick === q.correctAnswer;
        const verdict = $("verdict");
        verdict.textContent = right ? `Correct, it's ${q.correctAnswer}.` : `Not quite. The answer is ${q.correctAnswer}; you marked ${pick}.`;
        verdict.className = `note-verdict ${right ? "is-correct" : "is-wrong"}`;
        const area = q.ia ? `${q.ia.charAt(0).toUpperCase()}${q.ia.slice(1)}${q.specificIA ? ` (${q.specificIA})` : ""}. ` : "";
        $("explanation").replaceChildren(el("strong", { textContent: area }), q.description || "The answer key has no explanation for this question.");
    }

    const strip = $("strip").querySelectorAll("button");
    strip.forEach((b, n) => {
        const p = state.picks[n];
        b.className = p === undefined ? "" : p === state.questions[n].correctAnswer ? "is-correct" : "is-wrong";
        b.setAttribute("aria-current", String(n === i));
    });

    $("prev").disabled = i === 0;
    const next = $("next");
    const last = i === state.questions.length - 1;
    next.disabled = last;
    next.textContent = last ? "Last question" : "Next question";
}

function answer(letter) {
    const i = state.index;
    if (state.picks[i] !== undefined) return;
    state.picks[i] = letter;
    render();
    renderScore();
    $("next").focus({ preventScroll: true });
}

/* ---------- Score by area ---------- */

function renderScore() {
    const byArea = new Map();
    let done = 0, right = 0;
    state.questions.forEach((q, n) => {
        const pick = state.picks[n];
        if (pick === undefined) return;
        const ok = pick === q.correctAnswer;
        done++; if (ok) right++;
        const area = byArea.get(q.ia) || { done: 0, right: 0 };
        area.done++; if (ok) area.right++;
        byArea.set(q.ia, area);
    });
    const total = $("scoreTotal");
    document.querySelector(".score").classList.toggle("has-data", done > 0);
    if (!done) {
        total.textContent = "No answers yet";
        $("scoreList").replaceChildren();
        return;
    }
    total.replaceChildren(`${Math.round((right / done) * 100)}%`, el("small", { textContent: `${right} of ${done} correct` }));
    // Weakest areas first, so the list doubles as a study plan.
    const rows = [...byArea.entries()].sort((a, b) => a[1].right / a[1].done - b[1].right / b[1].done);
    $("scoreList").replaceChildren(...rows.map(([ia, s]) => {
        const pct = s.right / s.done;
        const row = el("li", { className: `score-row${s.done >= 3 && pct < 0.6 ? " is-weak" : ""}${s.done >= 3 && pct >= 0.8 ? " is-strong" : ""}` },
            el("span", { textContent: ia || "Unknown area" }),
            el("b", { textContent: `${s.right}/${s.done}` }),
            el("span", { className: "score-bar" }, el("span", { style: `width: ${pct * 100}%` })));
        return row;
    }));
}

/* ---------- Keyboard ---------- */

document.addEventListener("keydown", (e) => {
    if ($("sheet").hidden || $("upload").open || e.ctrlKey || e.metaKey || e.altKey) return;
    if (e.target instanceof Element && e.target.matches("input, select, textarea")) return;
    const key = e.key.toUpperCase();
    const n = LETTERS.indexOf(key) >= 0 ? LETTERS.indexOf(key) : ["1", "2", "3", "4"].indexOf(key);
    if (n >= 0) { answer(LETTERS[n]); e.preventDefault(); }
    else if (e.key === "ArrowRight") { go(state.index + 1); e.preventDefault(); }
    else if (e.key === "ArrowLeft") { go(state.index - 1); e.preventDefault(); }
});

// Collapsible sidebar panels remember whether they were left open.
for (const id of ["setupPanel", "scorePanel"]) {
    const panel = $(id);
    if (store.get(id) === "closed") panel.open = false;
    panel.addEventListener("toggle", () => store.set(id, panel.open ? "open" : "closed"));
}

$("setup").addEventListener("submit", startRound);
$("exam").addEventListener("change", (e) => { $("shuffle").checked = e.target.value === "all"; });
$("prev").addEventListener("click", () => go(state.index - 1));
$("next").addEventListener("click", () => go(state.index + 1));

initUpload({ api, onAdded: (result) => loadClusters(result.cluster) });
loadClusters();
