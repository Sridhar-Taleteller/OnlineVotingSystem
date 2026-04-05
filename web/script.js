const voter = localStorage.getItem("voterId");
const box = document.getElementById("candidates");

if (!voter) {
  window.location = "index.html";
}

async function load() {
  const res = await fetch("/api/candidates");
  const data = await res.json();

  box.innerHTML = "";

  data.candidates.forEach(c => {
    box.innerHTML += `
      <div class="card">
        <img src="${c.image}">
        <h2>${c.name}</h2>
        <p>${c.party}</p>
        <button onclick="vote('${c.id}')">Vote</button>
      </div>
    `;
  });
}

async function vote(id) {
  const form = new URLSearchParams();
  form.append("voterId", voter);
  form.append("candidateId", id);

  const res = await fetch("/api/vote", {
    method: "POST",
    body: form
  });

  const data = await res.json();

  if (!res.ok) {
    alert(data.error);
    return;
  }

  alert("Vote successful for " + data.candidateName);

  localStorage.clear();
  window.location = "index.html";
}

load();