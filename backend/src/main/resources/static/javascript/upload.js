// "Add an exam" dialog: upload a PDF, show what was imported (and anything the parser couldn't read).
const $ = (id) => document.getElementById(id);

export function initUpload({ api, onAdded }) {
    const dialog = $("upload");
    const form = $("uploadForm");
    const fileInput = $("file");
    const drop = $("drop");
    const dropText = $("dropText");
    const result = $("uploadResult");
    const submit = $("uploadSubmit");

    const showFile = () => {
        const file = fileInput.files[0];
        drop.classList.toggle("has-file", Boolean(file));
        if (file) dropText.textContent = file.name;
        else dropText.innerHTML = "Drop a PDF here or <u>choose a file</u>";
        $("examName").placeholder = file ? file.name : "Uses the file name";
    };

    const showResult = (kind, title, items = []) => {
        result.className = `upload-result ${kind ? `is-${kind}` : ""}`;
        const heading = document.createElement("strong");
        heading.textContent = title;
        result.replaceChildren(heading);
        if (items.length) {
            const list = document.createElement("ul");
            list.append(...items.map((text) => Object.assign(document.createElement("li"), { textContent: text })));
            result.append(list);
        }
    };

    $("openUpload").addEventListener("click", () => {
        form.reset();
        showFile();
        result.replaceChildren();
        dialog.showModal();
    });
    $("closeUpload").addEventListener("click", () => dialog.close());
    fileInput.addEventListener("change", showFile);

    for (const type of ["dragenter", "dragover"]) {
        drop.addEventListener(type, (e) => { e.preventDefault(); drop.classList.add("is-over"); });
    }
    for (const type of ["dragleave", "drop"]) {
        drop.addEventListener(type, () => drop.classList.remove("is-over"));
    }
    drop.addEventListener("drop", (e) => {
        e.preventDefault();
        if (e.dataTransfer.files.length) {
            fileInput.files = e.dataTransfer.files;
            showFile();
        }
    });

    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        const file = fileInput.files[0];
        if (!file) {
            showResult("error", "Choose a PDF to upload first.");
            return;
        }
        const data = new FormData();
        data.append("file", file);
        if ($("examName").value.trim()) data.append("examName", $("examName").value.trim());
        if ($("uploadCluster").value) data.append("cluster", $("uploadCluster").value);

        submit.disabled = true;
        submit.textContent = "Reading PDF…";
        showResult("", `Reading ${file.name}…`);
        try {
            const added = await api("exams/upload", { method: "POST", body: data });
            const title = `Added ${added.examName.replace(/\.pdf$/i, "")} to ${added.cluster}: ${added.questionCount} questions.`;
            showResult(added.warnings.length ? "" : "ok",
                added.warnings.length ? `${title} Some questions may need checking:` : title,
                added.warnings);
            form.reset();
            showFile();
            await onAdded(added);
        } catch (err) {
            showResult("error", err.message);
        } finally {
            submit.disabled = false;
            submit.textContent = "Add exam";
        }
    });
}
