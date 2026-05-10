import * as THREE from "https://cdn.jsdelivr.net/npm/three@0.164.1/build/three.module.js";

const canvas = document.getElementById("hologramScene");
const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(52, window.innerWidth / window.innerHeight, 0.1, 100);
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });

renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(window.innerWidth, window.innerHeight);
camera.position.set(0, 0.2, 8);

const group = new THREE.Group();
scene.add(group);

const shell = new THREE.Mesh(
  new THREE.IcosahedronGeometry(2.25, 2),
  new THREE.MeshBasicMaterial({ color: 0x36d5bb, wireframe: true, transparent: true, opacity: 0.2 })
);
const core = new THREE.Mesh(
  new THREE.OctahedronGeometry(1.22, 1),
  new THREE.MeshBasicMaterial({ color: 0x7be7d4, wireframe: true, transparent: true, opacity: 0.32 })
);
group.add(shell);
group.add(core);

const ringMaterial = new THREE.LineBasicMaterial({ color: 0x9debdc, transparent: true, opacity: 0.28 });
for (let i = 0; i < 4; i++) {
  const curve = new THREE.EllipseCurve(0, 0, 2.7 + i * 0.28, 1.04 + i * 0.12, 0, Math.PI * 2);
  const ring = new THREE.LineLoop(new THREE.BufferGeometry().setFromPoints(curve.getPoints(160)), ringMaterial);
  ring.rotation.x = Math.PI / 2.3;
  ring.rotation.y = i * 0.42;
  group.add(ring);
}

const particleGeometry = new THREE.BufferGeometry();
const particleCount = 800;
const positions = new Float32Array(particleCount * 3);
for (let i = 0; i < particleCount; i++) {
  const radius = 5 + Math.random() * 10;
  const theta = Math.random() * Math.PI * 2;
  const phi = Math.acos(2 * Math.random() - 1);
  positions[i * 3] = radius * Math.sin(phi) * Math.cos(theta);
  positions[i * 3 + 1] = radius * Math.sin(phi) * Math.sin(theta);
  positions[i * 3 + 2] = radius * Math.cos(phi);
}
particleGeometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
const particles = new THREE.Points(
  particleGeometry,
  new THREE.PointsMaterial({ color: 0xb8fff0, size: 0.018, transparent: true, opacity: 0.45 })
);
scene.add(particles);

const grid = new THREE.GridHelper(18, 42, 0x1c7e70, 0x13473f);
grid.material.transparent = true;
grid.material.opacity = 0.14;
grid.position.y = -3.1;
scene.add(grid);

const pointer = new THREE.Vector2(0, 0);
window.addEventListener("pointermove", function (event) {
  pointer.x = (event.clientX / window.innerWidth - 0.5) * 0.5;
  pointer.y = (event.clientY / window.innerHeight - 0.5) * 0.5;
});

window.addEventListener("resize", function () {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});

function animate(time) {
  const seconds = time * 0.001;
  group.rotation.y = seconds * 0.18 + pointer.x;
  group.rotation.x = Math.sin(seconds * 0.42) * 0.16 + pointer.y;
  core.rotation.y = -seconds * 0.34;
  particles.rotation.y = seconds * 0.018;
  grid.position.z = (seconds * 0.55) % 1;
  renderer.render(scene, camera);
  requestAnimationFrame(animate);
}

requestAnimationFrame(animate);
