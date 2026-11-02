import { readFile, writeFile } from 'node:fs/promises';

const [treeFile, reportFile] = process.argv.slice(2);
if (!treeFile || !reportFile) throw new Error('Uso: audit-maven-osv.mjs <dependency-tree.json> <report.json>');
const tree = JSON.parse(await readFile(treeFile, 'utf8'));
const packages = new Map();
function visit(node, excluded = false) {
  excluded ||= node.scope === 'test' || node.artifactId === 'spring-boot-devtools';
  if (excluded) return;
  if (node.groupId !== tree.groupId && node.type === 'jar' && node.version) {
    const name = `${node.groupId}:${node.artifactId}`;
    packages.set(`${name}@${node.version}`, { name, version: node.version });
  }
  for (const child of node.children || []) visit(child, excluded);
}
visit(tree);
const dependencies = [...packages.values()];
if (!dependencies.length) throw new Error('El árbol no contiene dependencias Maven de ejecución');
async function json(url, options) {
  const response = await fetch(url, { ...options, signal: AbortSignal.timeout(60_000) });
  if (!response.ok) throw new Error(`Consulta OSV falló: HTTP ${response.status}`);
  return response.json();
}
const data = await json('https://api.osv.dev/v1/querybatch', { method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ queries: dependencies.map(item => ({ package: { ecosystem: 'Maven', name: item.name }, version: item.version })) }),
});
const findings = [];
for (let index = 0; index < dependencies.length; index++) {
  for (const vulnerability of data.results[index]?.vulns || []) {
    const detail = await json(`https://api.osv.dev/v1/vulns/${encodeURIComponent(vulnerability.id)}`);
    if (detail.withdrawn) continue;
    findings.push({ ...dependencies[index], id: detail.id, summary: detail.summary, aliases: detail.aliases, affected: detail.affected, references: detail.references });
  }
}
await writeFile(reportFile, JSON.stringify({ checkedAt: new Date().toISOString(), packages: dependencies.length, findings }, null, 2));
console.log(`Dependencias Java de ejecución consultadas: ${dependencies.length}; avisos: ${findings.length}.`);
for (const finding of findings) console.log(`${finding.name}@${finding.version}: ${finding.id} ${finding.summary}`);
if (findings.length) process.exitCode = 1;
