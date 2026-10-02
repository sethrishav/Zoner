import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../api/client';
import { useToast } from '../../context/ToastContext';
import { getErrorMessage } from '../../api/errors';
import {
  X,
  Key,
  Globe,
  Plus,
  Trash2,
  Copy,
  Check,
  Cpu,
  Terminal,
  ShieldCheck,
  AlertCircle,
  ExternalLink,
} from 'lucide-react';

const COMMON_TIMEZONES = [
  'UTC',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Paris',
  'Europe/Berlin',
  'Asia/Dubai',
  'Asia/Kolkata',
  'Asia/Singapore',
  'Asia/Tokyo',
  'Australia/Sydney',
];

export default function SettingsModal({ isOpen, onClose }) {
  const { user, updateUser } = useAuth();
  const toast = useToast();

  const [activeTab, setActiveTab] = useState('mcp'); // 'mcp' | 'profile'
  const [tokens, setTokens] = useState([]);
  const [isLoadingTokens, setIsLoadingTokens] = useState(false);

  // New token form state
  const [isCreatingToken, setIsCreatingToken] = useState(false);
  const [tokenName, setTokenName] = useState('');
  const [expiresInDays, setExpiresInDays] = useState('90');
  const [newlyCreatedToken, setNewlyCreatedToken] = useState(null);
  const [copiedToken, setCopiedToken] = useState(false);
  const [copiedSnippet, setCopiedSnippet] = useState('');

  // Profile form state
  const [displayName, setDisplayName] = useState(user?.displayName || '');
  const [timeZone, setTimeZone] = useState(user?.timeZone || 'UTC');
  const [isSavingProfile, setIsSavingProfile] = useState(false);

  // Config snippet client tab & custom server URL override
  const [clientType, setClientType] = useState('cursor'); // 'cursor' | 'claude_desktop' | 'claude_code' | 'curl'
  const [customServerUrl, setCustomServerUrl] = useState('');
  const [isEditingServerUrl, setIsEditingServerUrl] = useState(false);

  useEffect(() => {
    if (isOpen) {
      fetchTokens();
      if (user) {
        setDisplayName(user.displayName || '');
        setTimeZone(user.timeZone || 'UTC');
      }
    } else {
      setNewlyCreatedToken(null);
      setIsCreatingToken(false);
    }
  }, [isOpen, user]);

  const fetchTokens = async () => {
    setIsLoadingTokens(true);
    try {
      const data = await api.tokens.list();
      setTokens(data || []);
    } catch (err) {
      toast.error('Failed to load access tokens');
    } finally {
      setIsLoadingTokens(false);
    }
  };

  const handleCreateToken = async (e) => {
    e.preventDefault();
    if (!tokenName.trim()) return;

    try {
      const res = await api.tokens.create({
        name: tokenName.trim(),
        expiresInDays: expiresInDays ? Number(expiresInDays) : null,
      });
      setNewlyCreatedToken(res.token);
      setTokenName('');
      setIsCreatingToken(false);
      toast.success('Personal Access Token generated!');
      fetchTokens();
    } catch (err) {
      toast.error(getErrorMessage(err));
    }
  };

  const handleRevokeToken = async (id, name) => {
    if (!confirm(`Are you sure you want to revoke "${name}"? Any connected AI clients will immediately lose access.`)) {
      return;
    }

    try {
      await api.tokens.revoke(id);
      toast.success(`Revoked "${name}"`);
      fetchTokens();
    } catch (err) {
      toast.error(getErrorMessage(err));
    }
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setIsSavingProfile(true);
    try {
      const updated = await api.user.updateMe({
        displayName: displayName.trim(),
        timeZone,
      });
      if (updateUser) updateUser(updated);
      toast.success('Profile and time zone updated!');
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setIsSavingProfile(false);
    }
  };

  const copyToClipboard = (text, type = 'token') => {
    navigator.clipboard.writeText(text);
    if (type === 'token') {
      setCopiedToken(true);
      setTimeout(() => setCopiedToken(false), 2000);
    } else {
      setCopiedSnippet(type);
      setTimeout(() => setCopiedSnippet(''), 2000);
    }
    toast.success('Copied to clipboard!');
  };

  if (!isOpen) return null;

  const sampleToken = newlyCreatedToken || 'YOUR_PERSONAL_ACCESS_TOKEN';
  
  const resolveDefaultApiBase = () => {
    if (import.meta.env.VITE_MCP_SERVER_URL) {
      return import.meta.env.VITE_MCP_SERVER_URL.replace(/\/api\/mcp\/?$/, '').replace(/\/+$/, '');
    }
    const apiUrl = import.meta.env.VITE_API_URL;
    if (apiUrl && !apiUrl.startsWith('/')) {
      try {
        const parsed = new URL(apiUrl);
        return parsed.origin;
      } catch {
        return apiUrl.replace(/\/api\/?$/, '');
      }
    }
    return typeof window !== 'undefined' ? window.location.origin : 'http://localhost:5173';
  };

  const defaultApiBase = resolveDefaultApiBase();
  const apiBase = customServerUrl.trim().replace(/\/+$/, '') || defaultApiBase;

  const cursorSnippet = `{
  "mcpServers": {
    "zoner-calendar": {
      "url": "${apiBase}/api/mcp",
      "headers": {
        "Authorization": "Bearer ${sampleToken}"
      }
    }
  }
}`;

  const claudeDesktopSnippet = `{
  "mcpServers": {
    "zoner-calendar": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "${apiBase}/api/mcp/sse",
        "--header",
        "Authorization: Bearer ${sampleToken}"
      ]
    }
  }
}`;

  const claudeCodeSnippet = `claude mcp add --transport http zoner-calendar ${apiBase}/api/mcp --header "Authorization: Bearer ${sampleToken}"`;

  const curlSnippet = `curl -X POST ${apiBase}/api/mcp \\
  -H "Authorization: Bearer ${sampleToken}" \\
  -H "Content-Type: application/json" \\
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'`;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-xs animate-in fade-in duration-100">
      <div className="bg-white rounded-2xl shadow-2xl max-w-2xl w-full border border-slate-100 flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-slate-100">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-brand-50 border border-brand-200/60 flex items-center justify-center text-brand-600">
              <Cpu className="w-4 h-4" />
            </div>
            <div>
              <h3 className="font-bold text-base text-slate-900">Settings & AI Integrations</h3>
              <p className="text-xs text-slate-500">Manage Model Context Protocol (MCP) access and preferences</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-50 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tab Navigation */}
        <div className="flex items-center gap-4 px-6 pt-3 border-b border-slate-100 text-xs font-semibold">
          <button
            onClick={() => setActiveTab('mcp')}
            className={`pb-3 flex items-center gap-2 border-b-2 transition-all cursor-pointer ${
              activeTab === 'mcp'
                ? 'border-brand-600 text-brand-600'
                : 'border-transparent text-slate-400 hover:text-slate-700'
            }`}
          >
            <Key className="w-3.5 h-3.5" />
            MCP & Access Tokens
          </button>
          <button
            onClick={() => setActiveTab('profile')}
            className={`pb-3 flex items-center gap-2 border-b-2 transition-all cursor-pointer ${
              activeTab === 'profile'
                ? 'border-brand-600 text-brand-600'
                : 'border-transparent text-slate-400 hover:text-slate-700'
            }`}
          >
            <Globe className="w-3.5 h-3.5" />
            Profile & Timezone
          </button>
        </div>

        {/* Content Body */}
        <div className="p-6 overflow-y-auto space-y-6 flex-1 text-xs">
          {activeTab === 'mcp' ? (
            <div className="space-y-6">
              {/* Informative Banner */}
              <div className="p-4 bg-gradient-to-r from-brand-50 to-indigo-50/60 border border-brand-200/80 rounded-2xl flex items-start gap-3">
                <ShieldCheck className="w-5 h-5 text-brand-600 shrink-0 mt-0.5" />
                <div className="space-y-1">
                  <h4 className="font-semibold text-brand-900 text-xs">Model Context Protocol (MCP) Server</h4>
                  <p className="text-slate-600 text-[11px] leading-relaxed">
                    Connect Zoner to <strong>Claude Desktop</strong>, <strong>Cursor</strong>, or <strong>Claude Code</strong>. AI assistants can list calendars, schedule events, check your availability, and search your schedule using 9 calendar tools.
                  </p>
                </div>
              </div>

              {/* Newly Created Token Display */}
              {newlyCreatedToken && (
                <div className="p-4 bg-amber-50 border border-amber-300 rounded-2xl space-y-2 animate-in fade-in duration-200">
                  <div className="flex items-center gap-2 text-amber-900 font-bold text-xs">
                    <AlertCircle className="w-4 h-4 text-amber-600" />
                    <span>Copy your Personal Access Token now!</span>
                  </div>
                  <p className="text-[11px] text-amber-800">
                    For security reasons, this token will <strong>never</strong> be shown again.
                  </p>
                  <div className="flex items-center gap-2 mt-2">
                    <input
                      type="text"
                      readOnly
                      value={newlyCreatedToken}
                      className="flex-1 font-mono text-[11px] bg-white border border-amber-300 rounded-xl px-3 py-2 text-slate-800 select-all"
                    />
                    <button
                      type="button"
                      onClick={() => copyToClipboard(newlyCreatedToken, 'token')}
                      className="px-3.5 py-2 bg-amber-600 hover:bg-amber-700 text-white rounded-xl font-semibold flex items-center gap-1.5 transition-colors cursor-pointer"
                    >
                      {copiedToken ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                      <span>{copiedToken ? 'Copied' : 'Copy'}</span>
                    </button>
                  </div>
                </div>
              )}

              {/* Tokens List Section */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h4 className="font-bold text-slate-800 text-xs uppercase tracking-wider">
                    Personal Access Tokens
                  </h4>
                  {!isCreatingToken && (
                    <button
                      type="button"
                      onClick={() => setIsCreatingToken(true)}
                      className="flex items-center gap-1.5 text-brand-600 hover:text-brand-700 font-semibold px-2.5 py-1 rounded-lg hover:bg-brand-50 transition-colors cursor-pointer"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      Generate Token
                    </button>
                  )}
                </div>

                {/* Inline Creation Form */}
                {isCreatingToken && (
                  <form onSubmit={handleCreateToken} className="p-4 bg-slate-50 border border-slate-200 rounded-2xl space-y-3 animate-in fade-in duration-100">
                    <h5 className="font-semibold text-slate-800 text-xs">Generate New Token</h5>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                      <div className="sm:col-span-2">
                        <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-500 mb-1">
                          Token Name
                        </label>
                        <input
                          type="text"
                          required
                          autoFocus
                          value={tokenName}
                          onChange={(e) => setTokenName(e.target.value)}
                          placeholder="e.g. Cursor IDE, Claude Desktop"
                          className="w-full text-xs font-medium rounded-xl border border-slate-200 bg-white py-2 px-3 text-slate-800 focus:outline-none focus:border-brand-500 shadow-2xs"
                        />
                      </div>
                      <div>
                        <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-500 mb-1">
                          Expiration
                        </label>
                        <select
                          value={expiresInDays}
                          onChange={(e) => setExpiresInDays(e.target.value)}
                          className="w-full text-xs font-medium rounded-xl border border-slate-200 bg-white py-2 px-3 text-slate-800 focus:outline-none focus:border-brand-500 shadow-2xs cursor-pointer"
                        >
                          <option value="30">30 days</option>
                          <option value="90">90 days</option>
                          <option value="365">1 year</option>
                          <option value="">No expiration</option>
                        </select>
                      </div>
                    </div>
                    <div className="flex items-center justify-end gap-2 pt-1">
                      <button
                        type="button"
                        onClick={() => setIsCreatingToken(false)}
                        className="px-3 py-1.5 text-xs text-slate-600 hover:bg-slate-200/70 rounded-lg transition-colors cursor-pointer"
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        className="px-4 py-1.5 text-xs font-semibold bg-brand-600 hover:bg-brand-700 text-white rounded-lg shadow-xs transition-colors cursor-pointer"
                      >
                        Generate Token
                      </button>
                    </div>
                  </form>
                )}

                {/* Tokens Table */}
                <div className="border border-slate-200 rounded-2xl overflow-hidden divide-y divide-slate-100 bg-white">
                  {tokens.length === 0 ? (
                    <div className="p-6 text-center text-slate-400">
                      No access tokens created yet. Generate one above to connect an AI client!
                    </div>
                  ) : (
                    tokens.map((token) => (
                      <div key={token.id} className="p-3.5 flex items-center justify-between gap-3 hover:bg-slate-50/60 transition-colors">
                        <div className="min-w-0 flex-1 space-y-0.5">
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-slate-800 text-xs truncate">{token.name}</span>
                            <span className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 border border-slate-200/60">
                              {token.tokenPrefix}
                            </span>
                            {token.revoked ? (
                              <span className="text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded bg-rose-50 text-rose-700 border border-rose-200">
                                Revoked
                              </span>
                            ) : (
                              <span className="text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200">
                                Active
                              </span>
                            )}
                          </div>
                          <div className="flex items-center gap-3 text-[11px] text-slate-400">
                            <span>Created: {new Date(token.createdAt).toLocaleDateString()}</span>
                            <span>•</span>
                            <span>Last used: {token.lastUsedAt ? new Date(token.lastUsedAt).toLocaleDateString() : 'Never'}</span>
                          </div>
                        </div>

                        {!token.revoked && (
                          <button
                            type="button"
                            onClick={() => handleRevokeToken(token.id, token.name)}
                            className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors cursor-pointer"
                            title="Revoke token"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </div>
                    ))
                  )}
                </div>
              </div>

              {/* Client Configuration Setup Snippets */}
              <div className="space-y-3 pt-2">
                <h4 className="font-bold text-slate-800 text-xs uppercase tracking-wider flex items-center gap-1.5">
                  <Terminal className="w-3.5 h-3.5 text-brand-600" />
                  Client Configuration Snippets
                </h4>

                {/* Sub tabs for clients */}
                <div className="flex items-center gap-2 border-b border-slate-200 pb-2">
                  <button
                    type="button"
                    onClick={() => setClientType('cursor')}
                    className={`px-3 py-1 rounded-lg font-medium transition-colors cursor-pointer ${
                      clientType === 'cursor' ? 'bg-slate-900 text-white font-semibold' : 'text-slate-600 hover:bg-slate-100'
                    }`}
                  >
                    Cursor
                  </button>
                  <button
                    type="button"
                    onClick={() => setClientType('claude_desktop')}
                    className={`px-3 py-1 rounded-lg font-medium transition-colors cursor-pointer ${
                      clientType === 'claude_desktop' ? 'bg-slate-900 text-white font-semibold' : 'text-slate-600 hover:bg-slate-100'
                    }`}
                  >
                    Claude Desktop
                  </button>
                  <button
                    type="button"
                    onClick={() => setClientType('claude_code')}
                    className={`px-3 py-1 rounded-lg font-medium transition-colors cursor-pointer ${
                      clientType === 'claude_code' ? 'bg-slate-900 text-white font-semibold' : 'text-slate-600 hover:bg-slate-100'
                    }`}
                  >
                    Claude Code
                  </button>
                  <button
                    type="button"
                    onClick={() => setClientType('curl')}
                    className={`px-3 py-1 rounded-lg font-medium transition-colors cursor-pointer ${
                      clientType === 'curl' ? 'bg-slate-900 text-white font-semibold' : 'text-slate-600 hover:bg-slate-100'
                    }`}
                  >
                    cURL Test
                  </button>
                </div>

                {/* Target server URL indicator & customization */}
                <div className="flex items-center justify-between gap-2 px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-[11px]">
                  <div className="flex items-center gap-1.5 overflow-hidden flex-1 min-w-0">
                    <span className="font-semibold text-slate-600 shrink-0">Endpoint Host:</span>
                    {isEditingServerUrl ? (
                      <input
                        type="text"
                        value={customServerUrl}
                        onChange={(e) => setCustomServerUrl(e.target.value)}
                        placeholder={defaultApiBase}
                        className="font-mono text-[11px] bg-white border border-brand-300 rounded px-2 py-0.5 text-slate-800 focus:outline-none focus:ring-1 focus:ring-brand-500 w-full max-w-xs"
                      />
                    ) : (
                      <span className="font-mono text-slate-700 truncate" title={apiBase}>
                        {apiBase}
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-1.5 shrink-0">
                    {isEditingServerUrl ? (
                      <>
                        <button
                          type="button"
                          onClick={() => {
                            setCustomServerUrl('');
                            setIsEditingServerUrl(false);
                          }}
                          className="text-[11px] text-slate-500 hover:text-slate-700 px-1.5 py-0.5"
                        >
                          Reset
                        </button>
                        <button
                          type="button"
                          onClick={() => setIsEditingServerUrl(false)}
                          className="text-[11px] text-brand-600 font-semibold px-2 py-0.5 bg-brand-50 rounded"
                        >
                          Done
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        onClick={() => {
                          if (!customServerUrl) setCustomServerUrl(defaultApiBase);
                          setIsEditingServerUrl(true);
                        }}
                        className="text-[11px] text-brand-600 hover:text-brand-700 font-semibold hover:underline"
                      >
                        {customServerUrl ? 'Edit URL' : 'Change URL'}
                      </button>
                    )}
                  </div>
                </div>

                {/* Code display */}
                <div className="relative group">
                  <pre className="p-4 bg-slate-900 text-slate-200 rounded-2xl font-mono text-[11px] overflow-x-auto leading-relaxed border border-slate-800">
                    {clientType === 'cursor' && cursorSnippet}
                    {clientType === 'claude_desktop' && claudeDesktopSnippet}
                    {clientType === 'claude_code' && claudeCodeSnippet}
                    {clientType === 'curl' && curlSnippet}
                  </pre>
                  <button
                    type="button"
                    onClick={() => {
                      const text =
                        clientType === 'cursor'
                          ? cursorSnippet
                          : clientType === 'claude_desktop'
                          ? claudeDesktopSnippet
                          : clientType === 'claude_code'
                          ? claudeCodeSnippet
                          : curlSnippet;
                      copyToClipboard(text, 'snippet');
                    }}
                    className="absolute top-3 right-3 px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-[10px] font-semibold flex items-center gap-1 transition-colors cursor-pointer border border-slate-700"
                  >
                    {copiedSnippet === 'snippet' ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                    <span>{copiedSnippet === 'snippet' ? 'Copied' : 'Copy Snippet'}</span>
                  </button>
                </div>
              </div>
            </div>
          ) : (
            /* Profile & Timezone Tab */
            <form onSubmit={handleSaveProfile} className="space-y-4 max-w-md">
              <div>
                <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-500 mb-1.5">
                  Display Name
                </label>
                <input
                  type="text"
                  required
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className="w-full text-xs font-medium rounded-xl border border-slate-200 bg-white py-2 px-3 text-slate-800 focus:outline-none focus:border-brand-500 shadow-2xs"
                />
              </div>

              <div>
                <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-500 mb-1.5">
                  Email Address
                </label>
                <input
                  type="email"
                  disabled
                  value={user?.email || ''}
                  className="w-full text-xs font-medium rounded-xl border border-slate-200 bg-slate-100 py-2 px-3 text-slate-500 cursor-not-allowed"
                />
                <span className="text-[10px] text-slate-400 mt-1 block">
                  Email address cannot be changed.
                </span>
              </div>

              <div>
                <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-500 mb-1.5">
                  IANA Primary Time Zone
                </label>
                <select
                  value={timeZone}
                  onChange={(e) => setTimeZone(e.target.value)}
                  className="w-full text-xs font-medium rounded-xl border border-slate-200 bg-white py-2 px-3 text-slate-800 focus:outline-none focus:border-brand-500 shadow-2xs cursor-pointer"
                >
                  {COMMON_TIMEZONES.map((tz) => (
                    <option key={tz} value={tz}>
                      {tz}
                    </option>
                  ))}
                </select>
                <span className="text-[10px] text-slate-400 mt-1 block">
                  Used by MCP AI tools and reminder notifications to interpret local event times.
                </span>
              </div>

              <div className="pt-2">
                <button
                  type="submit"
                  disabled={isSavingProfile}
                  className="px-5 py-2 bg-brand-600 hover:bg-brand-700 text-white font-semibold rounded-xl transition-colors cursor-pointer shadow-xs"
                >
                  {isSavingProfile ? 'Saving...' : 'Save Preferences'}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
