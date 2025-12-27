async function convertPdf() {
  const file = document.getElementById("pdfInput").files[0];
  if (!file) {
    alert("Please upload a PDF");
    return;
  }

  const reader = new FileReader();
  reader.onload = async function () {
    const typedArray = new Uint8Array(this.result);
    const pdf = await pdfjsLib.getDocument(typedArray).promise;

    let fullText = "";

    for (let i = 1; i <= pdf.numPages; i++) {
      const page = await pdf.getPage(i);
      const content = await page.getTextContent();
      const pageText = content.items.map(item => item.str).join(" ");
      fullText += pageText + "\n";
    }

    document.getElementById("resumeText").value = fullText;
  };

  reader.readAsArrayBuffer(file);
}

async function sendToBackend() {
  const resumeText = document.getElementById("resumeText").value;
  if (!resumeText.trim()) {
    alert("Resume text is empty");
    return;
  }

  const response = await fetch("http://localhost:8080/api/parse", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ resumeText })
  });

  const data = await response.json();

  if (data.success) {
    document.getElementById("jsonOutput").textContent =
      JSON.stringify(JSON.parse(data.reply), null, 2);
  } else {
    document.getElementById("jsonOutput").textContent = "Parsing failed";
  }
}
