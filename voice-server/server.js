const path = require("path");
const express = require("express");
const http = require("http");
const { Server } = require("socket.io");

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: "*" } });

// Serves game assets from the repository's public/ directory
app.use(express.static(path.join(__dirname, "..", "public")));

app.get("/health", (_req, res) => {
  res.json({ status: "ok", service: "vice-horizon-voice-signaling" });
});

io.on("connection", (socket) => {
  socket.on("join-voice-room", (roomId) => {
    if (!roomId || typeof roomId !== "string") return;
    const cleanRoomId = roomId.trim().slice(0, 128);
    if (!cleanRoomId) return;

    socket.join(cleanRoomId);
    socket.to(cleanRoomId).emit("user-connected", socket.id);
  });

  socket.on("signal", ({ to, signal } = {}) => {
    if (!to || !signal) return;
    io.to(to).emit("signal", { from: socket.id, signal });
  });

  socket.on("disconnecting", () => {
    for (const roomId of socket.rooms) {
      if (roomId !== socket.id) {
        socket.to(roomId).emit("user-disconnected", socket.id);
      }
    }
  });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`Game & Voice Server running on port ${PORT}`);
});
