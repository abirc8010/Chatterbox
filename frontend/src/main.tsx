import {
  StrictMode,
  Suspense,
  lazy,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { createRoot } from "react-dom/client";
import { Client } from "@stomp/stompjs";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  ChevronDown,
  FileImage,
  Menu,
  MessageCircle,
  Mic,
  Paperclip,
  Plus,
  Search,
  Send,
  Smile,
  Sparkles,
  Moon,
  Settings,
  Sun,
  UserPlus,
  Users,
  Video,
  X,
} from "lucide-react";
import "./styles.css";

type User = { email: string; displayName: string; avatarUrl?: string };
type Chat = {
  id: string;
  name: string;
  email: string;
  initials: string;
  color: string;
  avatarUrl?: string;
  preview: string;
  time: string;
  messages: Message[];
  online?: boolean;
  unread?: number;
  typing?: boolean;
};
type Message = {
  id: string;
  body: string;
  time: string;
  mine?: boolean;
  messageType?: string;
  mediaUrl?: string;
};
type ComposerAttachment = {
  name: string;
  kind: "file" | "image" | "audio" | "video" | "voice";
  url?: string;
  duration?: number;
  file?: File;
};
type Accent = "blue" | "green" | "orange" | "red" | "black" | "violet";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const EmojiPicker = lazy(() => import("emoji-picker-react"));

async function uploadMediaToCloudinary(file: File, uploadName = file.name): Promise<string> {
  if (file.size > 25 * 1024 * 1024) {
    throw new Error("Attachments must be 25 MB or smaller.");
  }
  const token = localStorage.getItem("chatterbox_token") ?? "";
  const form = new FormData();
  form.append("file", file, uploadName);
  form.append("token", token);
  const response = await fetch(`${API_URL}/api/v1/media/upload`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });
  if (!response.ok) {
    const responseBody = await response.text();
    let payload: { message?: string; detail?: string; error?: string } = {};
    try {
      payload = JSON.parse(responseBody) as typeof payload;
    } catch {
      // Non-JSON proxy and server errors are handled below.
    }
    const detail =
      payload.message ??
      payload.detail ??
      payload.error ??
      (responseBody.length < 240 ? responseBody.trim() : "");
    if (response.status === 401) {
      throw new Error("Your session expired. Sign in again, then retry the upload.");
    }
    if (response.status === 403) {
      throw new Error(detail || "The server denied this upload. Check your session and try again.");
    }
    if (response.status === 503) {
      throw new Error("Cloudinary is not configured. Add its three credentials to the root .env file and restart ./start.sh.");
    }
    if (response.status === 502) {
      throw new Error(detail || "Cloudinary rejected the upload. Check the cloud name, API key, API secret, and upload permissions in .env, then restart the backend.");
    }
    throw new Error(detail || `Upload failed (${response.status} ${response.statusText}).`);
  }
  const payload = (await response.json()) as { url?: string };
  if (!payload.url) throw new Error("Cloudinary did not return a media URL.");
  return new URL(payload.url, API_URL).toString();
}

function Avatar({
  chat,
  small = false,
}: {
  chat: Pick<Chat, "initials" | "color" | "online"> & { avatarUrl?: string };
  small?: boolean;
}) {
  return (
    <div className={`avatar ${chat.color} ${small ? "small" : ""}`}>
      {chat.avatarUrl ? (
        <img className="avatar-image" src={chat.avatarUrl} alt="" />
      ) : (
        chat.initials
      )}
      <span className={chat.online ? "presence online" : "presence"} />
    </div>
  );
}

function MessageBody({ message }: { message: Message }) {
  const [imageFailed, setImageFailed] = useState(false);
  const [audioFailed, setAudioFailed] = useState(false);
  const mediaUrl = message.mediaUrl;
  const messageType = (message.messageType ?? "").toUpperCase();
  const mediaPath = mediaUrl?.split(/[?#]/, 1)[0] ?? "";
  const isImage = messageType === "IMAGE" || /\.(avif|gif|jpe?g|png|webp)$/i.test(mediaPath);
  const isAudio =
    ["AUDIO", "VOICE"].includes(messageType) ||
    (!messageType.includes("VIDEO") &&
      /\.(aac|m4a|mp3|oga|ogg|opus|wav|weba)$/i.test(mediaPath));
  const isVideo =
    messageType === "VIDEO" ||
    (!messageType.includes("AUDIO") &&
      messageType !== "VOICE" &&
      /\.(m4v|mov|mp4|ogv)$/i.test(mediaPath));
  let filename = "Download attachment";
  if (mediaUrl) {
    try {
      filename =
        decodeURIComponent(new URL(mediaUrl, API_URL).pathname.split("/").pop() ?? "")
          .replace(/^[0-9a-f-]{36}-/i, "") || filename;
    } catch {
      // Keep a safe fallback label for malformed legacy URLs.
    }
  }
  if (isImage) filename = "Shared image";
  if (messageType === "VOICE") filename = "Voice message";
  if (isAudio && messageType !== "VOICE") filename = "Audio message";
  return (
    <div className="message-body">
      {mediaUrl && isImage && !imageFailed && (
        <a
          className="message-media-link"
          href={mediaUrl}
          target="_blank"
          rel="noreferrer"
          aria-label="Open shared image"
        >
          <img
            className="message-image"
            src={mediaUrl}
            alt={filename}
            loading="lazy"
            onError={() => setImageFailed(true)}
          />
        </a>
      )}
      {mediaUrl && isImage && imageFailed && (
        <a className="message-media-fallback" href={mediaUrl} target="_blank" rel="noreferrer">
          <FileImage size={20} />
          <span>Image unavailable</span>
          <small>Open original</small>
        </a>
      )}
      {mediaUrl && isAudio && !audioFailed && (
        <div className="audio-attachment">
          <span className="audio-attachment-icon"><Mic size={16} /></span>
          <div className="audio-attachment-player">
            <strong>{filename}</strong>
            <audio
              className="message-audio"
              src={mediaUrl}
              controls
              preload="metadata"
              aria-label={`Play ${filename.toLowerCase()}`}
              onError={() => setAudioFailed(true)}
            />
          </div>
        </div>
      )}
      {mediaUrl && isAudio && audioFailed && (
        <a className="message-media-fallback" href={mediaUrl} target="_blank" rel="noreferrer">
          <Mic size={20} />
          <span>Audio unavailable</span>
          <small>Open original</small>
        </a>
      )}
      {mediaUrl && isVideo && (
        <video className="message-video" src={mediaUrl} controls preload="metadata" />
      )}
      {mediaUrl && !isImage && !isAudio && !isVideo && (
        <a
          className="message-file"
          href={mediaUrl}
          target="_blank"
          rel="noreferrer"
          download={filename}
        >
          <FileImage size={17} />
          <span>{filename}</span>
          <span className="message-file-action">Open</span>
        </a>
      )}
      {message.body && (
        <ReactMarkdown
          remarkPlugins={[remarkGfm]}
          components={{
            a: ({ href, children, ...props }) => (
              <a href={href} target="_blank" rel="noreferrer" {...props}>
                {children}
              </a>
            ),
          }}
        >
          {message.body}
        </ReactMarkdown>
      )}
    </div>
  );
}

function App() {
  const [user, setUser] = useState<User | null>(() =>
    JSON.parse(localStorage.getItem("chatterbox_user") ?? "null"),
  );
  if (!user)
    return (
      <AuthScreen
        onAuthenticated={(nextUser, token) => {
          localStorage.setItem("chatterbox_token", token);
          localStorage.setItem("chatterbox_user", JSON.stringify(nextUser));
          setUser(nextUser);
        }}
      />
    );
  return (
    <Workspace
      user={user}
      onUserUpdate={(nextUser) => {
        localStorage.setItem("chatterbox_user", JSON.stringify(nextUser));
        setUser(nextUser);
      }}
      onLogout={() => {
        localStorage.clear();
        setUser(null);
      }}
    />
  );
}

function AuthScreen({
  onAuthenticated,
}: {
  onAuthenticated: (user: User, token: string) => void;
}) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError("");
    setLoading(true);
    try {
      const response = await fetch(`${API_URL}/api/v1/auth/${mode}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email,
          password,
          displayName: mode === "register" ? displayName : undefined,
        }),
      });
      const payload = await response.json();
      if (!response.ok)
        throw new Error(payload.message ?? "Unable to authenticate");
      onAuthenticated(payload.user, payload.token);
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Unable to reach Chatterbox API",
      );
    } finally {
      setLoading(false);
    }
  }
  return (
    <main className="auth-shell">
      <div className="auth-card">
        <div className="auth-brand">
          <div className="brand-mark">
            <Sparkles size={17} />
          </div>
          <span>chatterbox</span>
        </div>
        <div className="auth-copy">
          <span className="eyebrow">Private conversations, in sync</span>
          <h1>
            {mode === "login"
              ? "Welcome back."
              : "Make room for better conversations."}
          </h1>
          <p>
            {mode === "login"
              ? "Sign in to pick up where you left off."
              : "Create your account and start a private conversation."}
          </p>
        </div>
        <form className="auth-form" onSubmit={submit}>
          {mode === "register" && (
            <label>
              Name
              <input
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                placeholder="Your name"
                required
              />
            </label>
          )}
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
              required
            />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="At least 8 characters"
              minLength={8}
              required
            />
          </label>
          {error && <p className="form-error">{error}</p>}
          <button className="primary-button" disabled={loading}>
            {loading
              ? "Connecting..."
              : mode === "login"
                ? "Sign in"
                : "Create account"}
            <Send size={16} />
          </button>
        </form>
        <button
          className="auth-switch"
          onClick={() => {
            setMode(mode === "login" ? "register" : "login");
            setError("");
          }}
        >
          {mode === "login"
            ? "New to Chatterbox? Create an account"
            : "Already have an account? Sign in"}
        </button>
        <span className="auth-note">
          Your password is encrypted and never shared.
        </span>
      </div>
    </main>
  );
}

function Workspace({
  user,
  onUserUpdate,
  onLogout,
}: {
  user: User;
  onUserUpdate: (user: User) => void;
  onLogout: () => void;
}) {
  const [activeId, setActiveId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [draft, setDraft] = useState("");
  const [attachment, setAttachment] = useState<ComposerAttachment | null>(null);
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState("");
  const [emojiOpen, setEmojiOpen] = useState(false);
  const [recording, setRecording] = useState(false);
  const [chats, setChats] = useState<Chat[]>([]);
  const [darkMode, setDarkMode] = useState(
    () => localStorage.getItem("chatterbox_theme") === "dark",
  );
  const [accent, setAccent] = useState<Accent>(
    () => (localStorage.getItem("chatterbox_accent") as Accent) || "violet",
  );
  const [contactOpen, setContactOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [profileName, setProfileName] = useState(user.displayName);
  const [profilePhoto, setProfilePhoto] = useState(user.avatarUrl);
  const [photoDraft, setPhotoDraft] = useState<string | null>(null);
  const [cropPreview, setCropPreview] = useState<string | null>(null);
  const [photoZoom, setPhotoZoom] = useState(1);
  const [photoX, setPhotoX] = useState(0);
  const [photoY, setPhotoY] = useState(0);
  const photoInputRef = useRef<HTMLInputElement>(null);
  const attachmentInputRef = useRef<HTMLInputElement>(null);
  const imageInputRef = useRef<HTMLInputElement>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const recordingStartedRef = useRef(0);
  const stompRef = useRef<Client | null>(null);
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const activeIdRef = useRef<string | null>(null);
  const [contactQuery, setContactQuery] = useState("");
  const [contactResults, setContactResults] = useState<Chat[]>([]);
  const [contactError, setContactError] = useState("");
  const [addingContact, setAddingContact] = useState(false);
  const active = chats.find((chat) => chat.id === activeId) ?? null;
  useEffect(() => {
    activeIdRef.current = activeId;
  }, [activeId]);
  useEffect(() => {
    if (contactQuery.trim().length < 2) {
      setContactResults([]);
      return;
    }
    fetch(
      `${API_URL}/api/v1/users/search?email=${encodeURIComponent(contactQuery.trim())}`,
      {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("chatterbox_token") ?? ""}`,
        },
      },
    )
      .then((response) => (response.ok ? response.json() : []))
      .then((found: User[]) =>
        setContactResults(
          found.map((foundUser) => ({
            id: foundUser.email,
            name: foundUser.displayName,
            email: foundUser.email,
            initials: foundUser.displayName.slice(0, 2).toUpperCase(),
            color: "coral",
            avatarUrl: foundUser.avatarUrl,
            preview: "Add contact and start chatting",
            time: "",
            messages: [],
          })),
        ),
      )
      .catch(() => setContactResults([]));
  }, [contactQuery, user.email]);
  useEffect(() => {
    fetch(`${API_URL}/api/v1/conversations`, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem("chatterbox_token") ?? ""}`,
      },
    })
      .then((response) => (response.ok ? response.json() : []))
      .then(
        (found: Array<{ id: string; contact: User; unreadCount: number }>) =>
          setChats(
            found.map(({ id, contact, unreadCount }) => ({
              id,
              name: contact.displayName,
              email: contact.email,
              initials: contact.displayName.slice(0, 2).toUpperCase(),
              color: "coral",
              avatarUrl: contact.avatarUrl,
              preview: "Conversation",
              time: "",
              unread: unreadCount,
              messages: [],
            })),
          ),
      )
      .catch(() => setChats([]));
  }, [user.email]);
  useEffect(() => {
    const client = new Client({
      brokerURL: `${API_URL.replace(/^http/, "ws")}/ws-chat`,
      reconnectDelay: 3000,
    });
    client.onConnect = () => {
      client.subscribe("/topic/messages", (frame) => {
        const event = JSON.parse(frame.body) as {
          id: string;
          conversationId: string;
          senderEmail: string;
          body: string;
          createdAt: string;
          messageType?: string;
          mediaUrl?: string;
        };
        setChats((current) =>
          current.map((chat) =>
            chat.id === event.conversationId
              ? {
                  ...chat,
                  messages: chat.messages.some(
                    (message) => message.id === event.id,
                  )
                    ? chat.messages
                    : [
                        ...chat.messages,
                        {
                          id: event.id,
                          body: event.body,
                          messageType: event.messageType,
                          mediaUrl: event.mediaUrl,
                          time: new Date(event.createdAt).toLocaleTimeString(
                            [],
                            { hour: "numeric", minute: "2-digit" },
                          ),
                          mine: event.senderEmail === user.email,
                        },
                      ],
                  preview: event.body,
                  time: "Now",
                  unread:
                    event.senderEmail === user.email ||
                    chat.id === activeIdRef.current
                      ? 0
                      : (chat.unread ?? 0) + 1,
                }
              : chat,
          ),
        );
      });
      client.subscribe("/topic/typing", (frame) => {
        const event = JSON.parse(frame.body) as {
          conversationId: string;
          senderEmail: string;
          isTyping: boolean;
        };
        if (event.senderEmail === user.email) return;
        setChats((current) =>
          current.map((chat) =>
            chat.id === event.conversationId
              ? { ...chat, typing: event.isTyping }
              : chat,
          ),
        );
      });
      client.subscribe("/topic/presence", (frame) => {
        const event = JSON.parse(frame.body) as {
          email: string;
          displayName: string;
          avatarUrl?: string;
          online: boolean;
        };
        setChats((current) =>
          current.map((chat) =>
            chat.email === event.email
              ? {
                  ...chat,
                  name: event.displayName,
                  initials: event.displayName.slice(0, 2).toUpperCase(),
                  avatarUrl: event.avatarUrl,
                  online: event.online,
                }
              : chat,
          ),
        );
      });
      client.publish({
        destination: "/app/presence",
        body: JSON.stringify({ email: user.email, online: true }),
      });
    };
    client.activate();
    stompRef.current = client;
    const handleBeforeUnload = () =>
      client.publish({
        destination: "/app/presence",
        body: JSON.stringify({ email: user.email, online: false }),
      });
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => {
      window.removeEventListener("beforeunload", handleBeforeUnload);
      client.deactivate();
      stompRef.current = null;
    };
  }, [user.email]);
  useEffect(() => {
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setActiveId(null);
        setContactOpen(false);
        setProfileOpen(false);
        setSettingsOpen(false);
        setEmojiOpen(false);
      }
    };
    window.addEventListener("keydown", handleEscape);
    return () => window.removeEventListener("keydown", handleEscape);
  }, []);
  const visibleChats = useMemo(
    () =>
      chats.filter(
        (chat) =>
          chat.name.toLowerCase().includes(query.toLowerCase()) ||
          chat.email.toLowerCase().includes(query.toLowerCase()),
      ),
    [chats, query],
  );

  async function selectChat(chat: Chat): Promise<boolean> {
    let conversationId = chat.id;
    if (!chats.some((existing) => existing.id === chat.id)) {
      setAddingContact(true);
      setContactError("");
      try {
        const response = await fetch(`${API_URL}/api/v1/conversations`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("chatterbox_token") ?? ""}`,
          },
          body: JSON.stringify({ recipientEmail: chat.email }),
        });
        const payload = (await response.json().catch(() => ({}))) as {
          conversationId?: string;
          message?: string;
        };
        if (!response.ok || !payload.conversationId) {
          setContactError(
            payload.message ??
              "Could not add this contact. Is the backend running?",
          );
          setAddingContact(false);
          return false;
        }
        conversationId = payload.conversationId;
        setChats((current) => [...current, { ...chat, id: conversationId }]);
      } catch {
        setContactError(
          "Could not reach Chatterbox. Start the backend with ./start.sh.",
        );
        setAddingContact(false);
        return false;
      }
    }
    setActiveId(conversationId);
    setChats((current) =>
      current.map((item) =>
        item.id === conversationId ? { ...item, unread: 0 } : item,
      ),
    );
    fetch(`${API_URL}/api/v1/conversations/${conversationId}/read`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${localStorage.getItem("chatterbox_token") ?? ""}`,
      },
    }).catch(() => undefined);
    fetch(`${API_URL}/api/v1/conversations/${conversationId}/messages`, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem("chatterbox_token") ?? ""}`,
      },
    })
      .then((response) => (response.ok ? response.json() : []))
      .then(
        (
          found: Array<{
            id: string;
            body: string;
            senderEmail: string;
            createdAt: string;
            messageType?: string;
            mediaUrl?: string;
          }>,
        ) =>
          setChats((current) =>
            current.map((item) =>
              item.id === conversationId
                ? {
                    ...item,
                    messages: found.map((message) => ({
                      id: message.id,
                      body: message.body,
                      messageType: message.messageType,
                      mediaUrl: message.mediaUrl,
                      time: new Date(message.createdAt).toLocaleTimeString([], {
                        hour: "numeric",
                        minute: "2-digit",
                      }),
                      mine: message.senderEmail === user.email,
                    })),
                  }
                : item,
            ),
          ),
      );
    setQuery("");
    setAddingContact(false);
    return true;
  }
  function toggleTheme() {
    const next = !darkMode;
    setDarkMode(next);
    localStorage.setItem("chatterbox_theme", next ? "dark" : "light");
  }
  function openContacts() {
    setContactOpen(true);
    setContactQuery("");
    setContactError("");
  }
  function choosePhoto(file: File) {
    setPhotoDraft(URL.createObjectURL(file));
    setCropPreview(null);
    setPhotoZoom(1);
    setPhotoX(0);
    setPhotoY(0);
  }
  useEffect(() => {
    if (!photoDraft) {
      setCropPreview(null);
      return;
    }
    const image = new Image();
    image.onload = () => {
      const outputSize = 512;
      const cropSize =
        Math.min(image.naturalWidth, image.naturalHeight) / photoZoom;
      const maxX = image.naturalWidth - cropSize;
      const maxY = image.naturalHeight - cropSize;
      const sourceX = Math.max(
        0,
        Math.min(maxX, maxX / 2 + (photoX * maxX) / 2),
      );
      const sourceY = Math.max(
        0,
        Math.min(maxY, maxY / 2 + (photoY * maxY) / 2),
      );
      const canvas = document.createElement("canvas");
      canvas.width = outputSize;
      canvas.height = outputSize;
      canvas
        .getContext("2d")
        ?.drawImage(
          image,
          sourceX,
          sourceY,
          cropSize,
          cropSize,
          0,
          0,
          outputSize,
          outputSize,
        );
      setCropPreview(canvas.toDataURL("image/jpeg", 0.9));
    };
    image.src = photoDraft;
  }, [photoDraft, photoZoom, photoX, photoY]);
  function savePhotoCrop() {
    if (!cropPreview || !photoDraft) return;
    setProfilePhoto(cropPreview);
    setPhotoDraft(null);
    setCropPreview(null);
    URL.revokeObjectURL(photoDraft);
  }
  function handleDraftChange(value: string) {
    setDraft(value);
    if (!active || !stompRef.current?.connected) return;
    stompRef.current.publish({
      destination: "/app/chat.typing",
      body: JSON.stringify({
        conversationId: active.id,
        senderEmail: user.email,
        isTyping: value.length > 0,
      }),
    });
    if (typingTimerRef.current) clearTimeout(typingTimerRef.current);
    typingTimerRef.current = setTimeout(
      () =>
        stompRef.current?.publish({
          destination: "/app/chat.typing",
          body: JSON.stringify({
            conversationId: active.id,
            senderEmail: user.email,
            isTyping: false,
          }),
        }),
      1200,
    );
  }
  async function sendMessage(
    bodyOverride?: string,
    selectedAttachment = attachment,
  ): Promise<boolean> {
    const body = (bodyOverride ?? draft).trim();
    if (!body && !selectedAttachment) return false;
    if (!active) return false;
    if (!stompRef.current?.connected) {
      setSendError("Chat is connecting. Please try again in a moment.");
      return false;
    }
    setSending(true);
    setSendError("");
    let mediaUrl: string | undefined;
    try {
      if (selectedAttachment?.file) {
        mediaUrl = await uploadMediaToCloudinary(selectedAttachment.file);
      }
      if (!stompRef.current?.connected) {
        throw new Error("The chat connection was lost. Please try sending again.");
      }
      stompRef.current.publish({
        destination: "/app/chat.send",
        body: JSON.stringify({
          conversationId: active.id,
          senderEmail: user.email,
          body,
          messageType: selectedAttachment?.kind?.toUpperCase() ?? "TEXT",
          mediaUrl,
        }),
      });
      stompRef.current.publish({
        destination: "/app/chat.typing",
        body: JSON.stringify({
          conversationId: active.id,
          senderEmail: user.email,
          isTyping: false,
        }),
      });
      setDraft("");
      return true;
    } catch (cause) {
      setSendError(
        cause instanceof TypeError
          ? "Could not reach the media server. Check that the backend is running and try again."
          : cause instanceof Error
            ? cause.message
            : "Could not send this message.",
      );
      return false;
    } finally {
      setSending(false);
    }
  }
  function clearAttachment() {
    if (attachment?.url) URL.revokeObjectURL(attachment.url);
    setAttachment(null);
  }
  function selectAttachment(file: File, preferredKind: "file" | "image") {
    if (attachment?.url) URL.revokeObjectURL(attachment.url);
    const kind = file.type.startsWith("image/")
      ? "image"
      : file.type.startsWith("video/")
        ? "video"
        : file.type.startsWith("audio/")
          ? "audio"
          : preferredKind;
    setAttachment({
      name: file.name,
      kind,
      file,
      url: URL.createObjectURL(file),
    });
  }
  async function toggleRecording() {
    if (recording && recorderRef.current) {
      recorderRef.current.stop();
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) return;
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const recorder = new MediaRecorder(stream);
    const chunks: Blob[] = [];
    recorder.ondataavailable = (event) => chunks.push(event.data);
    recorder.onstop = () => {
      stream.getTracks().forEach((track) => track.stop());
      const blob = new Blob(chunks, { type: "audio/webm" });
      if (attachment?.url) URL.revokeObjectURL(attachment.url);
      setAttachment({
        name: "Voice note",
        kind: "voice",
        file: new File([blob], "voice-note.webm", { type: "audio/webm" }),
        url: URL.createObjectURL(blob),
        duration: Math.round((Date.now() - recordingStartedRef.current) / 1000),
      });
      setRecording(false);
    };
    recorderRef.current = recorder;
    recordingStartedRef.current = Date.now();
    recorder.start();
    setRecording(true);
  }
  async function sendComposerMessage() {
    if (sending) return;
    const selected = attachment;
    const body = draft;
    setEmojiOpen(false);
    if (await sendMessage(body, selected)) clearAttachment();
  }
  async function saveProfileChanges() {
    setSendError("");
    setSending(true);
    try {
      let avatarUrl = profilePhoto;
      if (avatarUrl?.startsWith("data:")) {
        const response = await fetch(avatarUrl);
        const blob = await response.blob();
        avatarUrl = await uploadMediaToCloudinary(
          new File([blob], "profile-avatar.jpg", { type: blob.type || "image/jpeg" }),
          "profile-avatar.jpg",
        );
      }
      const response = await fetch(`${API_URL}/api/v1/users/me`, {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${localStorage.getItem("chatterbox_token") ?? ""}`,
        },
        body: JSON.stringify({
          displayName: profileName.trim() || user.displayName,
          avatarUrl,
        }),
      });
      if (!response.ok) {
        const body = (await response.json().catch(() => ({}))) as { message?: string };
        throw new Error(body.message ?? `Could not save profile (${response.status}).`);
      }
      const updated = (await response.json()) as User;
      setProfilePhoto(updated.avatarUrl);
      onUserUpdate(updated);
      setChats((current) =>
        current.map((chat) =>
          chat.email === updated.email
            ? { ...chat, name: updated.displayName, avatarUrl: updated.avatarUrl }
            : chat,
        ),
      );
      stompRef.current?.publish({
        destination: "/app/presence",
        body: JSON.stringify({ email: updated.email, online: true }),
      });
      setProfileOpen(false);
    } catch (cause) {
      setSendError(
        cause instanceof TypeError
          ? "Could not reach the server while saving your profile."
          : cause instanceof Error
            ? cause.message
            : "Could not save profile changes.",
      );
    } finally {
      setSending(false);
    }
  }
  function changeAccent(next: Accent) {
    setAccent(next);
    localStorage.setItem("chatterbox_accent", next);
  }

  return (
    <main className={`app-shell ${darkMode ? "dark" : ""} accent-${accent}`}>
      <aside className="sidebar">
        <div className="brand-row">
          <div className="brand-mark">
            <MessageCircle size={17} />
          </div>
          <span>chatterbox</span>
          <button
            className="icon-button sidebar-menu"
            aria-label="Toggle theme"
            onClick={toggleTheme}
          >
            {darkMode ? <Sun size={18} /> : <Moon size={18} />}
          </button>
        </div>
        <div className="profile-row">
          <Avatar
            chat={{
              initials: user.displayName.slice(0, 2).toUpperCase(),
              color: "ink",
              avatarUrl: user.avatarUrl,
            }}
            small
          />
          <div>
            <strong>{user.displayName}</strong>
            <span className="muted">{user.email}</span>
          </div>
          <button
            className="icon-button"
            onClick={() => setSettingsOpen(!settingsOpen)}
            title="Settings"
          >
            <Settings size={16} />
          </button>
          {settingsOpen && (
            <div className="profile-menu">
              <button
                onClick={() => {
                  setProfileOpen(true);
                  setSettingsOpen(false);
                }}
              >
                <Settings size={14} /> Account settings
              </button>
              <button onClick={onLogout}>
                <ChevronDown size={14} /> Sign out
              </button>
            </div>
          )}
        </div>
        <div className="section-heading">
          <span>Messages</span>
          <button
            className="icon-button add-button"
            aria-label="Add contact"
            onClick={openContacts}
          >
            <Plus size={17} />
          </button>
        </div>
        <label className="search-box">
          <Search size={16} />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search conversations"
          />
        </label>
        <nav className="conversation-list">
          {visibleChats.length ? (
            visibleChats.map((chat) => (
              <button
                className={`conversation ${chat.id === activeId ? "active" : ""}`}
                key={chat.id}
                onClick={() => selectChat(chat)}
              >
                <Avatar chat={chat} small />
                <span className="conversation-copy">
                  <strong>{chat.name}</strong>
                  <span>{chat.typing ? "typing..." : chat.email}</span>
                </span>
                <span className="conversation-meta">
                  <small>{chat.time}</small>
                  {chat.unread ? <b>{chat.unread}</b> : null}
                </span>
              </button>
            ))
          ) : (
            <div className="empty-sidebar">
              <Users size={20} />
              <span>
                {query ? "No matching conversation" : "No conversations yet"}
              </span>
              <small>
                {query
                  ? "This search only checks your contacts."
                  : "Use + to search globally."}
              </small>
            </div>
          )}
        </nav>
      </aside>
      <section className="chat-panel">
        {!active ? (
          <div className="welcome-panel">
            <div className="welcome-mark">
              <MessageCircle size={24} />
            </div>
            <h1>Your inbox is yours.</h1>
            <p>Find someone by email to start a private conversation.</p>
            <button className="primary-button" onClick={openContacts}>
              Find someone <Search size={16} />
            </button>
          </div>
        ) : (
          <>
            <header className="chat-header">
              <div className="chat-person">
                <Avatar chat={active} />
                <div>
                  <h1>{active.name}</h1>
                  <span>
                    <i
                      className={`presence-dot ${active.online ? "online" : "offline"}`}
                    />{" "}
                    {active.online ? "Online" : "Offline"}
                    {active.typing ? " · typing..." : ""}
                  </span>
                </div>
              </div>
              <div className="header-actions">
                <button className="round-action" aria-label="Start video call">
                  <Video size={18} />
                </button>
                <button
                  className="round-action"
                  aria-label="Attach media"
                  onClick={() => attachmentInputRef.current?.click()}
                >
                  <Paperclip size={18} />
                </button>
              </div>
            </header>
            <div className="thread">
              <div className="date-divider">
                <span>Today</span>
              </div>
              {active.messages.map((message) => (
                <div
                  className={`message-row ${message.mine ? "mine" : ""}`}
                  key={message.id}
                >
                  <div className={`message-content ${message.mediaUrl ? "has-media" : ""}`}>
                    <div className="bubble">
                      <MessageBody message={message} />
                    </div>
                    <div className="message-meta">
                      <span>{message.time}</span>
                    </div>
                  </div>
                </div>
              ))}
              {active.typing && (
                <div className="typing-indicator">
                  <span />
                  <span />
                  <span /> {active.name} is typing
                </div>
              )}
            </div>
            <div className="composer-wrap">
              <input
                ref={attachmentInputRef}
                className="composer-file-input"
                type="file"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) selectAttachment(file, "file");
                  event.currentTarget.value = "";
                }}
              />
              <input
                ref={imageInputRef}
                className="composer-file-input"
                type="file"
                accept="image/*"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) selectAttachment(file, "image");
                  event.currentTarget.value = "";
                }}
              />
              {attachment && (
                <div className="attachment-preview">
                  <span>
                    {attachment.kind === "voice" ? "Voice note" : attachment.name}
                    <small>{attachment.kind} attachment</small>
                  </span>
                  <button
                    onClick={clearAttachment}
                    aria-label="Remove attachment"
                    disabled={sending}
                  >
                    <X size={14} />
                  </button>
                </div>
              )}
              <div className="composer">
                <button
                  className="attach-button"
                  aria-label="Attach file"
                  onClick={() => attachmentInputRef.current?.click()}
                >
                  <Paperclip size={19} />
                </button>
                <textarea
                  value={draft}
                  onChange={(event) => handleDraftChange(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" && !event.shiftKey) {
                      event.preventDefault();
                      void sendComposerMessage();
                    }
                  }}
                  placeholder="Write a message..."
                  rows={1}
                  disabled={sending}
                />
                <div className="composer-actions">
                  <button
                    aria-label="Add image"
                    onClick={() => imageInputRef.current?.click()}
                    disabled={sending}
                  >
                    <FileImage size={18} />
                  </button>
                  <button
                    aria-label={
                      recording ? "Stop recording" : "Record voice note"
                    }
                    className={recording ? "recording" : ""}
                    onClick={toggleRecording}
                    disabled={sending}
                  >
                    <Mic size={18} />
                  </button>
                  <button
                    aria-label="Add emoji"
                    onClick={() => setEmojiOpen(!emojiOpen)}
                    disabled={sending}
                  >
                    <Smile size={18} />
                  </button>
                  <button
                    className="send-button"
                    onClick={() => void sendComposerMessage()}
                    aria-label={sending ? "Sending message" : "Send message"}
                    disabled={sending || (!draft.trim() && !attachment)}
                  >
                    <Send size={17} />
                  </button>
                </div>
              </div>
              <div className="composer-hint">Markdown supported · Shift+Enter for a new line</div>
              {sendError && <div className="send-error" role="alert">{sendError}</div>}
              {emojiOpen && (
                <div className="emoji-picker-popover">
                  <Suspense fallback={<div className="emoji-loading">Loading emojis…</div>}>
                    <EmojiPicker
                      onEmojiClick={(emojiData) =>
                        setDraft((value) => `${value}${emojiData.emoji}`)
                      }
                      theme={darkMode ? "dark" : "light"}
                      emojiStyle="native"
                      height={420}
                      width="min(350px, calc(100vw - 24px))"
                      lazyLoadEmojis
                      previewConfig={{ showPreview: false }}
                    />
                  </Suspense>
                </div>
              )}
            </div>
          </>
        )}
      </section>
      {contactOpen && (
        <div
          className="modal-backdrop"
          onClick={() => !addingContact && setContactOpen(false)}
        >
          <section
            className="contact-modal"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <div>
                <span className="eyebrow">Global search</span>
                <h2>Find someone new</h2>
              </div>
              <button
                className="icon-button"
                onClick={() => !addingContact && setContactOpen(false)}
                aria-label="Close"
              >
                <X size={18} />
              </button>
            </div>
            <p className="modal-copy">
              Search all Chatterbox users by email. Your existing contacts stay
              in the sidebar search.
            </p>
            <label className="modal-search">
              <Search size={16} />
              <input
                autoFocus
                value={contactQuery}
                onChange={(event) => {
                  setContactQuery(event.target.value);
                  setContactError("");
                }}
                placeholder="Start typing an email"
                type="text"
              />
            </label>
            {contactError && (
              <p className="form-error contact-error">{contactError}</p>
            )}
            <div className="contact-results">
              {contactQuery.length < 2 ? (
                <div className="modal-empty">
                  Type at least 2 characters to search globally.
                </div>
              ) : contactResults.filter(
                  (contact) =>
                    !chats.some(
                      (chat) =>
                        chat.email.toLowerCase() ===
                        contact.email.toLowerCase(),
                    ),
                ).length ? (
                contactResults
                  .filter(
                    (contact) =>
                      !chats.some(
                        (chat) =>
                          chat.email.toLowerCase() ===
                          contact.email.toLowerCase(),
                      ),
                  )
                  .map((contact) => (
                    <div className="contact-result" key={contact.id}>
                      <Avatar chat={contact} small />
                      <div>
                        <strong>{contact.name}</strong>
                        <span>{contact.email}</span>
                      </div>
                      <button
                        className="add-contact"
                        disabled={addingContact}
                        onClick={async () => {
                          if (await selectChat(contact)) setContactOpen(false);
                        }}
                      >
                        {addingContact ? (
                          "Adding..."
                        ) : (
                          <>
                            <Plus size={15} /> Add
                          </>
                        )}
                      </button>
                    </div>
                  ))
              ) : (
                <div className="modal-empty">No new users found.</div>
              )}
            </div>
          </section>
        </div>
      )}
      {profileOpen && (
        <div className="modal-backdrop" onClick={() => setProfileOpen(false)}>
          <section
            className="settings-modal"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <div>
                <span className="eyebrow">Settings</span>
                <h2>Your profile</h2>
              </div>
              <button
                className="icon-button"
                onClick={() => setProfileOpen(false)}
                aria-label="Close"
              >
                <X size={18} />
              </button>
            </div>
            <div className="profile-editor">
              <Avatar
                chat={{
                  initials: profileName.slice(0, 2).toUpperCase(),
                  color: "ink",
                  avatarUrl: profilePhoto,
                }}
              />
              <button
                className="secondary-button"
                onClick={() => photoInputRef.current?.click()}
              >
                Choose photo
              </button>
              <input
                ref={photoInputRef}
                className="photo-input"
                type="file"
                accept="image/*"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) choosePhoto(file);
                }}
              />
            </div>
            <label className="setting-field">
              Display name
              <input
                value={profileName}
                onChange={(event) => setProfileName(event.target.value)}
              />
            </label>
            <label className="setting-field">
              Email
              <input value={user.email} disabled />
            </label>
            <button
              className="primary-button save-profile"
              onClick={() => void saveProfileChanges()}
              disabled={sending}
            >
              {sending ? "Saving..." : "Save changes"}
            </button>
            {sendError && <p className="send-error" role="alert">{sendError}</p>}
          </section>
        </div>
      )}
      {photoDraft && (
        <div
          className="modal-backdrop"
          onClick={() => {
            setPhotoDraft(null);
            setCropPreview(null);
          }}
        >
          <section
            className="crop-modal"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <div>
                <span className="eyebrow">Profile photo</span>
                <h2>Adjust your photo</h2>
              </div>
              <button
                className="icon-button"
                onClick={() => {
                  setPhotoDraft(null);
                  setCropPreview(null);
                }}
                aria-label="Close"
              >
                <X size={18} />
              </button>
            </div>
            <div className="crop-preview">
              {cropPreview ? (
                <img src={cropPreview} alt="Profile crop preview" />
              ) : (
                <span className="modal-empty">Preparing preview...</span>
              )}
            </div>
            <label className="crop-control">
              Zoom{" "}
              <input
                type="range"
                min="1"
                max="3"
                step=".01"
                value={photoZoom}
                onChange={(event) => setPhotoZoom(Number(event.target.value))}
              />
            </label>
            <label className="crop-control">
              Horizontal{" "}
              <input
                type="range"
                min="-1"
                max="1"
                step=".01"
                value={photoX}
                onChange={(event) => setPhotoX(Number(event.target.value))}
              />
            </label>
            <label className="crop-control">
              Vertical{" "}
              <input
                type="range"
                min="-1"
                max="1"
                step=".01"
                value={photoY}
                onChange={(event) => setPhotoY(Number(event.target.value))}
              />
            </label>
            <div className="crop-actions">
              <button
                className="secondary-button"
                onClick={() => {
                  setPhotoDraft(null);
                  setCropPreview(null);
                }}
              >
                Cancel
              </button>
              <button
                className="primary-button"
                onClick={savePhotoCrop}
                disabled={!cropPreview}
              >
                Use this photo
              </button>
            </div>
          </section>
        </div>
      )}
    </main>
  );
}

function SettingsIcon() {
  return (
    <svg
      width="17"
      height="17"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <path d="M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4Z" />
      <path
        d="m19.4 15 .1.1a2 2 0 0 1-2.8 2.8l-.1-.1a2 2 0 0 0-3.4 1.4v.2a2 2 0 0 1-4 0v-.2a2 2 0 0 0-3.4-1.4l-.1.1a2 2 0 0 1-2.8-2.8l.1-.1a2 2 0 0 0-1.4-3.4h-.2a2 2 0 0 1 0-4h.2a2 2 0 0 0 1.4-3.4l-.1-.1A2 2 0 0 1 5.7 1.3l.1.1a2 2 0 0 0 3.4-1.4v-.2a2 2 0 0 1 4 0V0a2 2 0 0 0 3.4 1.4l.1-.1a2 2 0 0 1 2.8 2.8l-.1.1a2 2 0 0 0 1.4 3.4h.2a2 2 0 0 1 0 4h-.2a2 2 0 0 0-1.4 3.4Z"
        transform="translate(0 1) scale(.92)"
      />
    </svg>
  );
}
function PhoneIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .3 2 .7 2.8a2 2 0 0 1-.5 2.1L8 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.5c.9.4 1.8.6 2.8.7a2 2 0 0 1 1.8 2.1Z" />
    </svg>
  );
}

export default App;

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
