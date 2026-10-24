import React, { useState, useMemo, useRef, useEffect } from 'react';
import {
  PhoneIncoming,
  PhoneOutgoing,
  PhoneMissed,
  PhoneOff,
  Search,
  Plus,
  Trash2,
  Smartphone,
  Tablet,
  X,
  MoreVertical,
  ArrowLeft,
  ShieldCheck,
  Check,
  Clock,
  CheckCheck,
  Edit3,
  Zap,
  Settings as SettingsIcon,
  Phone,
  Mic,
  MicOff,
  Volume2,
  VolumeX,
  History,
  Pause,
  Disc,
  Video,
  Users,
  MessageSquare,
  ChevronUp,
  Copy,
  ExternalLink,
  Bug,
  Download,
  RefreshCw,
  Sparkles
} from 'lucide-react';

export type CallType = 1 | 2 | 3 | 5; // 1: Incoming, 2: Outgoing, 3: Missed, 5: Rejected

export interface CallLogItem {
  id: string;
  name: string;
  number: string;
  type: CallType;
  date: Date;
  durationSeconds: number;
  simSlot: 1 | 2;
}

export interface ContactCallGroup {
  key: string;
  name: string;
  number: string;
  latestItem: CallLogItem;
  calls: CallLogItem[];
  consecutiveMissedCount: number;
}

const DEVICE_PRESETS = {
  compact: { label: 'Compact (360×740)', width: 360, height: 740, icon: Smartphone },
  standard: { label: 'Standard (393×852)', width: 393, height: 852, icon: Smartphone },
  large: { label: 'Large (428×926)', width: 428, height: 926, icon: Smartphone },
  foldable: { label: 'Foldable (673×841)', width: 673, height: 841, icon: Tablet },
};

// Material You Dynamic Lilac Status Colors
const CALL_TYPES: {
  type: CallType;
  label: string;
  icon: React.ElementType;
  iconColor: string;
  bgLight: string;
}[] = [
  {
    type: 1,
    label: 'Incoming',
    icon: PhoneIncoming,
    iconColor: 'text-[#10B981]',
    bgLight: 'bg-[#10B981]/12',
  },
  {
    type: 2,
    label: 'Outgoing',
    icon: PhoneOutgoing,
    iconColor: 'text-[#3B82F6]',
    bgLight: 'bg-[#3B82F6]/12',
  },
  {
    type: 3,
    label: 'Missed',
    icon: PhoneMissed,
    iconColor: 'text-[#F43F5E]',
    bgLight: 'bg-[#F43F5E]/12',
  },
  {
    type: 5,
    label: 'Rejected',
    icon: PhoneOff,
    iconColor: 'text-[#F59E0B]',
    bgLight: 'bg-[#F59E0B]/12',
  },
];

const INITIAL_LOGS: CallLogItem[] = [
  {
    id: '1',
    name: 'Shalini 🐼',
    number: '+91 62617 49807',
    type: 3,
    date: new Date(Date.now() - 1000 * 60 * 35),
    durationSeconds: 0,
    simSlot: 1,
  },
  {
    id: '2',
    name: 'Shalini 🐼',
    number: '+91 62617 49807',
    type: 3,
    date: new Date(Date.now() - 1000 * 60 * 75),
    durationSeconds: 0,
    simSlot: 1,
  },
  {
    id: '3',
    name: 'Shalini 🐼',
    number: '+91 62617 49807',
    type: 3,
    date: new Date(Date.now() - 1000 * 60 * 120),
    durationSeconds: 0,
    simSlot: 1,
  },
  {
    id: '4',
    name: 'Shalini 🐼',
    number: '+91 62617 49807',
    type: 1,
    date: new Date(Date.now() - 1000 * 60 * 60 * 4),
    durationSeconds: 310,
    simSlot: 1,
  },
  {
    id: '5',
    name: 'Aarav Sharma',
    number: '+91 98765 43210',
    type: 1,
    date: new Date(Date.now() - 1000 * 60 * 60 * 6),
    durationSeconds: 342,
    simSlot: 1,
  },
  {
    id: '6',
    name: 'Priya Nair',
    number: '+91 91234 56789',
    type: 2,
    date: new Date(Date.now() - 1000 * 60 * 60 * 12),
    durationSeconds: 125,
    simSlot: 2,
  },
  {
    id: '7',
    name: 'Customer Support',
    number: '1800-208-9898',
    type: 5,
    date: new Date(Date.now() - 1000 * 60 * 60 * 28),
    durationSeconds: 0,
    simSlot: 2,
  },
];

function formatDuration(totalSeconds: number): string {
  if (totalSeconds <= 0) return '0s';
  const hrs = Math.floor(totalSeconds / 3600);
  const mins = Math.floor((totalSeconds % 3600) / 60);
  const secs = totalSeconds % 60;
  if (hrs > 0) {
    if (mins > 0 && secs > 0) return `${hrs}h ${mins}m ${secs}s`;
    if (mins > 0) return `${hrs}h ${mins}m`;
    if (secs > 0) return `${hrs}h ${secs}s`;
    return `${hrs}h`;
  }
  if (mins > 0) {
    return secs > 0 ? `${mins}m ${secs}s` : `${mins}m`;
  }
  return `${secs}s`;
}

function formatCallTimer(sec: number): string {
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
}

const MOCK_CONTACTS = [
  { name: 'Shalini 🐼', number: '+91 62617 49807', initials: 'SP', color: 'bg-purple-100 text-purple-700' },
  { name: 'Rohan Verma', number: '+91 98765 43210', initials: 'RV', color: 'bg-blue-100 text-blue-700' },
  { name: 'Priya Nair', number: '+91 91234 56789', initials: 'PN', color: 'bg-emerald-100 text-emerald-700' },
  { name: 'Aarav Sharma', number: '+91 98765 43210', initials: 'AS', color: 'bg-indigo-100 text-indigo-700' },
  { name: 'Papa (Dad)', number: '+91 98220 11223', initials: 'PD', color: 'bg-amber-100 text-amber-700' },
  { name: 'Mummy (Mom)', number: '+91 98234 56789', initials: 'MM', color: 'bg-rose-100 text-rose-700' },
];

function formatCallTime(date: Date): string {
  const now = new Date();
  const isToday = now.toDateString() === date.toDateString();
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  const isYesterday = yesterday.toDateString() === date.toDateString();

  const timeStr = date.toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  });

  if (isToday) return timeStr;
  if (isYesterday) return `Yesterday`;
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function formatFullDateTime(date: Date): string {
  const now = new Date();
  const isToday = now.toDateString() === date.toDateString();
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  const isYesterday = yesterday.toDateString() === date.toDateString();

  const timeStr = date.toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  });

  if (isToday) return `Today • ${timeStr}`;
  if (isYesterday) return `Yesterday • ${timeStr}`;
  return `${date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })} • ${timeStr}`;
}

const FAKE_CALL_PRESETS = [
  { name: 'Boss', number: '+91 98123 45678' },
  { name: 'Mom', number: '+91 98980 12345' },
  { name: 'Doctor', number: '+91 98234 56789' },
  { name: 'Pizza', number: '+91 98765 00000' },
  { name: 'Police', number: '112' },
];

const DTMF_FREQS: Record<string, [number, number]> = {
  '1': [697, 1209],
  '2': [697, 1336],
  '3': [697, 1477],
  '4': [770, 1209],
  '5': [770, 1336],
  '6': [770, 1477],
  '7': [852, 1209],
  '8': [852, 1336],
  '9': [852, 1477],
  '*': [941, 1209],
  '0': [941, 1336],
  '#': [941, 1477],
};

function playTone(freq1: number, freq2: number, duration: number) {
  try {
    const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx();
    const osc1 = ctx.createOscillator();
    const osc2 = ctx.createOscillator();
    const gain = ctx.createGain();

    osc1.frequency.value = freq1;
    osc2.frequency.value = freq2;

    osc1.connect(gain);
    osc2.connect(gain);
    gain.connect(ctx.destination);

    gain.gain.setValueAtTime(0.08, ctx.currentTime);
    osc1.start();
    osc2.start();

    osc1.stop(ctx.currentTime + duration);
    osc2.stop(ctx.currentTime + duration);
  } catch (e) {
    // Autoplay policy or unsupported audio context
  }
}

function AppLogo({ size = 32 }: { size?: number }) {
  return (
    <img
      src="/logo.png"
      alt="Call Craft"
      style={{ width: `${size}px`, height: `${size}px` }}
      className="rounded-xl shadow-xs object-cover"
    />
  );
}

export default function App() {
  const [deviceKey, setDeviceKey] = useState<keyof typeof DEVICE_PRESETS>('standard');
  const [currentScreen, setCurrentScreen] = useState<'recents' | 'settings'>('recents');
  const [splashState, setSplashState] = useState<'visible' | 'fading' | 'hidden'>('visible');
  const [logs, setLogs] = useState<CallLogItem[]>(INITIAL_LOGS);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeCategoryFilter, setActiveCategoryFilter] = useState<number | 'ALL'>('ALL');
  const [isDialerRoleGranted, setIsDialerRoleGranted] = useState(true);
  const [hasCallLogPermission, setHasCallLogPermission] = useState(true);
  const [hasContactsPermission, setHasContactsPermission] = useState(true);
  const [hasPhoneStatePermission, setHasPhoneStatePermission] = useState(true);
  const [toastState, setToastState] = useState<{ message: string; isExiting: boolean } | null>(null);
  const [updateStatus, setUpdateStatus] = useState<'idle' | 'checking' | 'latest' | 'available' | 'error'>('idle');
  const [latestReleaseInfo, setLatestReleaseInfo] = useState<{ version: string; url: string; notes: string } | null>(null);

  const showToast = (message: string) => {
    setToastState({ message, isExiting: false });
    setTimeout(() => {
      setToastState(prev => (prev && prev.message === message ? { ...prev, isExiting: true } : prev));
      setTimeout(() => {
        setToastState(prev => (prev && prev.message === message ? null : prev));
      }, 200);
    }, 2000);
  };

  const triggerCopy = (label: string, text: string) => {
    navigator.clipboard?.writeText(text);
    showToast(`${label} copied: ${text}`);
  };

  useEffect(() => {
    const fadeTimer = setTimeout(() => {
      setSplashState('fading');
      const hideTimer = setTimeout(() => {
        setSplashState('hidden');
      }, 300);
      return () => clearTimeout(hideTimer);
    }, 1200);
    return () => clearTimeout(fadeTimer);
  }, []);

  const replaySplash = () => {
    setSplashState('visible');
    setTimeout(() => {
      setSplashState('fading');
      setTimeout(() => setSplashState('hidden'), 300);
    }, 1200);
  };

  const checkGithubUpdates = async () => {
    setUpdateStatus('checking');
    try {
      const res = await fetch('https://api.github.com/repos/sachinmandawi/Call-Craft/releases/latest');
      if (res.ok) {
        const data = await res.json();
        const tag = (data.tag_name || '').replace('v', '').trim();
        const apkAsset = (data.assets || []).find((a: any) => a.name.endsWith('.apk'));
        const downloadUrl = apkAsset?.browser_download_url || data.html_url;
        if (tag === '1.0.0') {
          setUpdateStatus('latest');
        } else {
          setLatestReleaseInfo({ version: tag || '1.0.0', url: downloadUrl, notes: data.body || 'Bug fixes and improvements' });
          setUpdateStatus('available');
        }
      } else {
        setUpdateStatus('latest');
      }
    } catch {
      setUpdateStatus('error');
    }
  };

  // Hardware SIM Simulation State (Dual SIM, SIM 1 Only, SIM 2 Only)
  const [simHardwareMode, setSimHardwareMode] = useState<'dual' | 'sim1_only' | 'sim2_only'>('dual');
  const hasSim1 = simHardwareMode === 'dual' || simHardwareMode === 'sim1_only';
  const hasSim2 = simHardwareMode === 'dual' || simHardwareMode === 'sim2_only';
  const defaultSlot: 1 | 2 = hasSim1 ? 1 : 2;

  // Multi-Select States
  const [isSelectionMode, setIsSelectionMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [showBulkActions, setShowBulkActions] = useState(false);

  // Google Phone Standard: Contact Call History Sheet State
  const [viewingGroup, setViewingGroup] = useState<ContactCallGroup | null>(null);

  // Contact Picker State
  const [isContactPickerOpen, setIsContactPickerOpen] = useState(false);
  const [contactSearch, setContactSearch] = useState('');

  // Add / Edit Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<CallLogItem | null>(null);

  // 3-Dot Dropdown Menu State
  const [isTopMenuOpen, setIsTopMenuOpen] = useState(false);

  // Fake Call Simulator States
  const [isFakeCallModalOpen, setIsFakeCallModalOpen] = useState(false);
  const [fakeCallBrand, setFakeCallBrand] = useState<'pixel' | 'samsung' | 'hyperos' | 'ios'>('pixel');
  const [fakeCallName, setFakeCallName] = useState('Boss');
  const [fakeCallNumber, setFakeCallNumber] = useState('+91 98765 43210');
  const [fakeCallSimSlot, setFakeCallSimSlot] = useState<1 | 2>(defaultSlot);
  const [fakeCallDelaySec, setFakeCallDelaySec] = useState<number>(3);
  const [fakeCallMinsInput, setFakeCallMinsInput] = useState('0');
  const [fakeCallSecsInput, setFakeCallSecsInput] = useState('3');
  const [fakeCallSound, setFakeCallSound] = useState(true);
  const [fakeCallVibrate, setFakeCallVibrate] = useState(true);
  const [fakeCallSaveToLog, setFakeCallSaveToLog] = useState(true);
  const [fakeCallCountdown, setFakeCallCountdown] = useState<number | null>(null);
  const [fakeCallState, setFakeCallState] = useState<'idle' | 'incoming' | 'active'>('idle');
  const [fakeCallElapsedSec, setFakeCallElapsedSec] = useState(0);
  const [isKeypadOpen, setIsKeypadOpen] = useState(false);
  const [dialedDigits, setDialedDigits] = useState('');
  const [isMuted, setIsMuted] = useState(false);
  const [isSpeakerOn, setIsSpeakerOn] = useState(false);
  const [isHold, setIsHold] = useState(false);
  const [isRecording, setIsRecording] = useState(false);

  // Form State
  const [formName, setFormName] = useState('');
  const [formNumber, setFormNumber] = useState('');
  const [formType, setFormType] = useState<CallType>(1);
  const [formDate, setFormDate] = useState<Date>(new Date());
  const [formDuration, setFormDuration] = useState<number>(60);
  const [formSimSlot, setFormSimSlot] = useState<1 | 2>(defaultSlot);
  const [durationHours, setDurationHours] = useState<string>('0');
  const [durationMins, setDurationMins] = useState<string>('1');
  const [durationSecs, setDurationSecs] = useState<string>('0');

  // Google Phone Standard: Contact Grouping
  const groupedLogs: ContactCallGroup[] = useMemo(() => {
    const map = new Map<string, CallLogItem[]>();
    for (const item of logs) {
      const cleanNumber = item.number.replace(/\s+/g, '').replace(/-/g, '');
      const digitsOnly = cleanNumber.replace(/\D/g, '');
      const key = digitsOnly.length >= 10 ? digitsOnly.slice(-10) : (cleanNumber || item.name.trim().toLowerCase());
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(item);
    }

    const result: ContactCallGroup[] = [];
    for (const [key, callList] of map.entries()) {
      const sorted = [...callList].sort((a, b) => b.date.getTime() - a.date.getTime());
      const latest = sorted[0];

      // Count consecutive missed calls at top
      let missedCount = 0;
      if (latest.type === 3) {
        for (const c of sorted) {
          if (c.type === 3) missedCount++;
          else break;
        }
      }

      result.push({
        key,
        name: latest.name,
        number: latest.number,
        latestItem: latest,
        calls: sorted,
        consecutiveMissedCount: missedCount,
      });
    }

    return result.sort((a, b) => b.latestItem.date.getTime() - a.latestItem.date.getTime());
  }, [logs]);

  // Sync viewing group if logs change
  useEffect(() => {
    if (viewingGroup) {
      const updated = groupedLogs.find((g) => g.key === viewingGroup.key);
      setViewingGroup(updated || null);
    }
  }, [groupedLogs]);

  // Fake Call Countdown Timer
  useEffect(() => {
    let timer: any = null;
    if (fakeCallCountdown !== null && fakeCallCountdown > 0) {
      timer = setTimeout(() => {
        setFakeCallCountdown((prev) => (prev !== null && prev > 1 ? prev - 1 : 0));
      }, 1000);
    } else if (fakeCallCountdown === 0) {
      setFakeCallCountdown(null);
      setFakeCallState('incoming');
    }
    return () => clearTimeout(timer);
  }, [fakeCallCountdown]);

  // Fake Call Active In-Call Duration Timer
  useEffect(() => {
    let interval: any = null;
    if (fakeCallState === 'active') {
      interval = setInterval(() => {
        setFakeCallElapsedSec((prev) => prev + 1);
      }, 1000);
    } else {
      setFakeCallElapsedSec(0);
      setIsKeypadOpen(false);
      setDialedDigits('');
      setIsHold(false);
      setIsRecording(false);
    }
    return () => clearInterval(interval);
  }, [fakeCallState]);

  // Fake Call Ringtone Audio Loop
  useEffect(() => {
    if (fakeCallState !== 'incoming' || !fakeCallSound) return;
    let isCancelled = false;
    let interval: any = null;

    const ring = () => {
      if (isCancelled) return;
      playTone(440, 480, 1.2);
    };

    ring();
    interval = setInterval(ring, 3000);

    return () => {
      isCancelled = true;
      clearInterval(interval);
    };
  }, [fakeCallState, fakeCallSound]);

  const allFilteredGroups = useMemo(() => {
    if (!searchQuery.trim()) return groupedLogs;
    return groupedLogs.filter(
      (g) =>
        g.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        g.number.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [groupedLogs, searchQuery]);

  const missedFilteredGroups = useMemo(() => {
    const filtered = groupedLogs.filter(
      (g) => g.latestItem.type === 3 || g.consecutiveMissedCount > 0
    );
    if (!searchQuery.trim()) return filtered;
    return filtered.filter(
      (g) =>
        g.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        g.number.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [groupedLogs, searchQuery]);

  const activeGroups = activeCategoryFilter === 'ALL' ? allFilteredGroups : missedFilteredGroups;

  const isGroupSelected = (group: ContactCallGroup) => {
    return group.calls.every((c) => selectedIds.has(c.id));
  };

  const toggleGroupSelect = (group: ContactCallGroup) => {
    const ids = group.calls.map((c) => c.id);
    setSelectedIds((prev) => {
      const next = new Set(prev);
      const allSelected = ids.every((id) => next.has(id));
      if (allSelected) {
        ids.forEach((id) => next.delete(id));
      } else {
        ids.forEach((id) => next.add(id));
      }
      return next;
    });
  };

  const handleSelectAllToggle = () => {
    const allVisibleCallIds = activeGroups.flatMap((g) => g.calls.map((c) => c.id));
    const allSelected = allVisibleCallIds.every((id) => selectedIds.has(id));
    if (allSelected) setSelectedIds(new Set());
    else setSelectedIds(new Set(allVisibleCallIds));
  };

  const openAddModal = (initialPrefill?: { name: string; number: string; simSlot?: 1 | 2 }) => {
    setEditingItem(null);
    setFormName(initialPrefill?.name || '');
    setFormNumber(initialPrefill?.number || '');
    setFormType(initialPrefill ? 2 : 1);
    setFormDate(new Date());
    setFormDuration(60);
    setFormSimSlot(initialPrefill?.simSlot || defaultSlot);
    setDurationHours('0');
    setDurationMins('1');
    setDurationSecs('0');
    setIsModalOpen(true);
  };

  const openEditModal = (item: CallLogItem) => {
    setEditingItem(item);
    setFormName(item.name);
    setFormNumber(item.number);
    setFormType(item.type);
    setFormDate(new Date(item.date.getTime()));
    setFormSimSlot(item.simSlot || defaultSlot);
    const dur = item.durationSeconds;
    setFormDuration(dur);
    setDurationHours(Math.floor(dur / 3600).toString());
    setDurationMins(Math.floor((dur % 3600) / 60).toString());
    setDurationSecs((dur % 60).toString());
    setIsModalOpen(true);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formNumber.trim()) return;

    const payload: CallLogItem = {
      id: editingItem ? editingItem.id : Date.now().toString(),
      name: formName.trim() || formNumber.trim(),
      number: formNumber.trim(),
      type: formType,
      date: formDate,
      durationSeconds: formType === 3 || formType === 5 ? 0 : formDuration,
      simSlot: formSimSlot,
    };

    if (editingItem) {
      setLogs((prev) => prev.map((l) => (l.id === editingItem.id ? payload : l)));
    } else {
      setLogs((prev) => [payload, ...prev]);
    }
    setIsModalOpen(false);
  };

  const handleBulkDeleteConfirm = () => {
    setLogs((prev) => prev.filter((item) => !selectedIds.has(item.id)));
    setSelectedIds(new Set());
    setIsSelectionMode(false);
    setShowDeleteConfirm(false);
  };

  const handleBulkTypeChange = (targetType: CallType) => {
    setLogs((prev) =>
      prev.map((item) => {
        if (selectedIds.has(item.id)) {
          const dur = targetType === 3 || targetType === 5 ? 0 : item.durationSeconds === 0 ? 60 : item.durationSeconds;
          return { ...item, type: targetType, durationSeconds: dur };
        }
        return item;
      })
    );
    setSelectedIds(new Set());
    setIsSelectionMode(false);
    setShowBulkActions(false);
  };

  const handleBulkSimChange = (targetSim: 1 | 2) => {
    setLogs((prev) =>
      prev.map((item) => {
        if (selectedIds.has(item.id)) {
          return { ...item, simSlot: targetSim };
        }
        return item;
      })
    );
    setSelectedIds(new Set());
    setIsSelectionMode(false);
    setShowBulkActions(false);
  };

  const handleDeleteAllForGroup = (group: ContactCallGroup) => {
    const idsToDelete = new Set(group.calls.map((c) => c.id));
    setLogs((prev) => prev.filter((item) => !idsToDelete.has(item.id)));
    setViewingGroup(null);
  };

  const activeDevice = DEVICE_PRESETS[deviceKey];
  const touchStartXRef = useRef(0);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col items-center py-6 px-4 select-none font-sans">
      {/* Top Device Switcher & SIM Hardware Controls */}
      <div className="w-full max-w-md flex items-center justify-between mb-4 px-2">
        <div className="flex items-center gap-2.5">
          <AppLogo size={28} />
          <div>
            <h2 className="text-xs font-bold tracking-tight">
              <span className="text-white">Call</span> <span className="text-[#A87FFB]">Craft</span>
            </h2>
            <p className="text-[10px] text-[#6750A4] font-semibold">Google Phone Standard</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Device Size Switcher */}
          <div className="hidden sm:flex items-center gap-0.5 bg-slate-900 p-1 rounded-xl border border-slate-800 text-[10px]">
            {(['compact', 'standard', 'large'] as const).map((key) => (
              <button
                key={key}
                onClick={() => setDeviceKey(key)}
                className={`px-2 py-0.5 rounded-lg font-bold transition capitalize ${
                  deviceKey === key ? 'bg-slate-700 text-white shadow-xs' : 'text-slate-400 hover:text-white'
                }`}
              >
                {key}
              </button>
            ))}
          </div>

          {/* Live SIM Hardware State Switcher */}
          <div className="flex items-center gap-1 bg-slate-900 p-1 rounded-xl border border-slate-800 text-[10px]">
            <span className="text-slate-400 px-1 font-bold">SIM:</span>
            {(['dual', 'sim1_only', 'sim2_only'] as const).map((mode) => (
              <button
                key={mode}
                onClick={() => setSimHardwareMode(mode)}
                className={`px-2 py-0.5 rounded-lg font-bold transition ${
                  simHardwareMode === mode ? 'bg-[#6750A4] text-white shadow-xs' : 'text-slate-400 hover:text-white'
                }`}
              >
                {mode === 'dual' ? 'Dual' : mode === 'sim1_only' ? 'SIM 1' : 'SIM 2'}
              </button>
            ))}
          </div>

          {/* Replay Splash Button */}
          <button
            onClick={replaySplash}
            className="flex items-center gap-1.5 bg-slate-900 hover:bg-slate-800 text-[#A87FFB] px-2.5 py-1.5 rounded-xl border border-slate-800 text-[10px] font-bold transition shadow-xs"
            title="Replay Splash Screen"
          >
            <Sparkles className="w-3.5 h-3.5 text-[#A87FFB]" />
            <span className="hidden sm:inline">Splash</span>
          </button>
        </div>
      </div>

      {/* Material You Pixel Phone Frame */}
      <div
        style={{ width: `${activeDevice.width}px`, height: `${activeDevice.height}px` }}
        className="relative bg-[#FEF7FF] text-[#1D1B20] rounded-[44px] border-[10px] border-slate-900 shadow-2xl overflow-hidden flex flex-col transition-all duration-300"
      >
        {/* Android Status Bar */}
        <div className="h-10 px-7 pt-2 flex items-center justify-between text-xs font-bold text-[#49454F] shrink-0">
          <span>9:41</span>
          <div className="w-20 h-4 bg-slate-900 rounded-full" />
          <div className="flex items-center gap-1.5 text-[11px]">
            <span>{hasSim1 && hasSim2 ? '5G • 5G' : '5G'}</span>
            <span>100%</span>
          </div>
        </div>

        {/* SCREEN CAROUSEL CONTAINER */}
        <div className="flex-1 overflow-hidden relative w-full flex">
          <div
            className="flex w-full h-full transition-transform duration-300 ease-[cubic-bezier(0.2,0,0,1)]"
            style={{
              transform: currentScreen === 'settings' ? 'translateX(-100%)' : 'translateX(0%)',
            }}
          >
            {/* SCREEN 1: CALL CRAFT HOME */}
            <div className="w-full h-full shrink-0 flex flex-col overflow-hidden relative">
              {/* Contextual Top Bar during Selection Mode OR Standard Brand Header */}
              {isSelectionMode ? (
                <div className="px-5 pt-4 pb-2 shrink-0 flex items-center justify-between bg-[#F3EDF7] border-b border-[#E7E0EC] animate-in fade-in duration-200">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => {
                        setIsSelectionMode(false);
                        setSelectedIds(new Set());
                      }}
                      className="p-1.5 rounded-full hover:bg-[#ECE6F0] text-[#1D1B20] transition"
                    >
                      <X className="w-5 h-5" />
                    </button>
                    <span className="text-sm font-black text-[#1D1B20]">
                      {(() => {
                        const count = activeGroups.filter((g) => g.calls.some((c) => selectedIds.has(c.id))).length;
                        return selectedIds.size > count && count > 0
                          ? `${count} selected (${selectedIds.size} calls)`
                          : `${count} selected`;
                      })()}
                    </span>
                  </div>

                  <div className="flex items-center gap-1">
                    <button
                      onClick={handleSelectAllToggle}
                      className="p-2 rounded-full hover:bg-[#ECE6F0] text-[#6750A4] transition"
                    >
                      <CheckCheck className="w-5 h-5" />
                    </button>

                    {selectedIds.size > 0 && (
                      <>
                        <button
                          onClick={() => setShowBulkActions(true)}
                          className="p-2 rounded-full hover:bg-[#ECE6F0] text-[#6750A4] transition"
                        >
                          <Edit3 className="w-5 h-5" />
                        </button>
                        <button
                          onClick={() => setShowDeleteConfirm(true)}
                          className="p-2 rounded-full hover:bg-rose-100 text-[#F43F5E] transition"
                        >
                          <Trash2 className="w-5 h-5" />
                        </button>
                      </>
                    )}
                  </div>
                </div>
              ) : (
                <div className="px-6 pt-5 pb-3 shrink-0">
                  <div className="flex items-center justify-between">
                    <h1 className="text-2xl font-black tracking-tight leading-none">
                      <span className="text-[#1D1B20]">Call</span>{' '}
                      <span className="text-[#6750A4]">Craft</span>
                    </h1>

                    <div className="relative">
                      <button
                        onClick={() => setIsTopMenuOpen(!isTopMenuOpen)}
                        className="p-2 -mr-2 text-[#49454F] hover:text-[#1D1B20] hover:bg-black/5 rounded-full transition"
                      >
                        <MoreVertical className="w-5 h-5" />
                      </button>

                      {isTopMenuOpen && (
                        <>
                          <div
                            className="fixed inset-0 z-40"
                            onClick={() => setIsTopMenuOpen(false)}
                          />
                          <div className="absolute right-0 top-full mt-2 w-[220px] bg-[#FEF7FF] rounded-[20px] shadow-2xl border border-[#E7E0EC] py-2 z-50 animate-in fade-in zoom-in-95 duration-150">
                            <button
                              onClick={() => {
                                setIsTopMenuOpen(false);
                                setIsFakeCallModalOpen(true);
                              }}
                              className="w-full px-3.5 py-2.5 text-left text-xs font-bold text-[#1D1B20] hover:bg-[#F3EDF7] flex items-center gap-2.5 transition"
                            >
                              <div className="w-[30px] h-[30px] rounded-[10px] bg-amber-500/15 text-amber-600 flex items-center justify-center shrink-0">
                                <Zap className="w-4 h-4 fill-amber-500" />
                              </div>
                              <div className="truncate">
                                <div className="text-xs font-bold text-[#1D1B20]">Fake Call Simulator</div>
                                <div className="text-[10px] text-[#79747E] font-normal truncate">Escape awkward situations</div>
                              </div>
                            </button>

                            <div className="h-px bg-[#E7E0EC] my-1 mx-3" />

                            <button
                              onClick={() => {
                                setIsTopMenuOpen(false);
                                setCurrentScreen('settings');
                              }}
                              className="w-full px-3.5 py-2.5 text-left text-xs font-bold text-[#1D1B20] hover:bg-[#F3EDF7] flex items-center gap-2.5 transition"
                            >
                              <div className="w-[30px] h-[30px] rounded-[10px] bg-[#6750A4]/15 text-[#6750A4] flex items-center justify-center shrink-0">
                                <SettingsIcon className="w-4 h-4" />
                              </div>
                              <div className="truncate">
                                <div className="text-xs font-bold text-[#1D1B20]">Settings</div>
                                <div className="text-[10px] text-[#79747E] font-normal truncate">Dialer role & system</div>
                              </div>
                            </button>
                          </div>
                        </>
                      )}
                    </div>
                  </div>
                </div>
              )}

              {/* Fake Call Active Countdown Banner */}
              {fakeCallCountdown !== null && (
                <div className="mx-5 mb-2 bg-gradient-to-r from-amber-500 to-amber-600 text-white rounded-2xl p-3 shadow-md flex items-center justify-between animate-pulse">
                  <div className="flex items-center gap-2.5 min-w-0">
                    <div className="w-8 h-8 rounded-xl bg-white/20 flex items-center justify-center shrink-0">
                      <Zap className="w-4 h-4 text-white fill-white" />
                    </div>
                    <div className="min-w-0">
                      <div className="text-xs font-black truncate">Fake Call in {fakeCallCountdown}s</div>
                      <div className="text-[10px] text-amber-100 font-medium truncate">{fakeCallName} ({fakeCallNumber})</div>
                    </div>
                  </div>
                  <button
                    onClick={() => setFakeCallCountdown(null)}
                    className="px-2.5 py-1 rounded-lg bg-black/20 hover:bg-black/30 text-[11px] font-bold shrink-0 transition"
                  >
                    Cancel
                  </button>
                </div>
              )}

              {/* Sliding Segmented Filter (All Calls / Missed) */}
              <div className="px-5 py-2 shrink-0">
                <div className="relative flex bg-[#ECE6F0] p-1 rounded-2xl select-none">
                  <div
                    className="absolute top-1 bottom-1 left-1 w-[calc(50%-4px)] bg-[#FEF7FF] rounded-xl shadow-xs transition-transform duration-300 ease-[cubic-bezier(0.2,0,0,1)] pointer-events-none"
                    style={{
                      transform: activeCategoryFilter === 'ALL' ? 'translateX(0%)' : 'translateX(100%)',
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => setActiveCategoryFilter('ALL')}
                    className={`relative z-10 flex-1 py-1.5 rounded-xl text-xs font-bold transition-colors duration-200 text-center ${
                      activeCategoryFilter === 'ALL' ? 'text-[#1D1B20]' : 'text-[#49454F] hover:text-[#1D1B20]'
                    }`}
                  >
                    All Calls
                  </button>
                  <button
                    type="button"
                    onClick={() => setActiveCategoryFilter(3)}
                    className={`relative z-10 flex-1 py-1.5 rounded-xl text-xs font-bold transition-colors duration-200 text-center ${
                      activeCategoryFilter === 3 ? 'text-[#F43F5E]' : 'text-[#49454F] hover:text-[#1D1B20]'
                    }`}
                  >
                    Missed
                  </button>
                </div>
              </div>

              {/* Full-Width Clean Search Bar (NO EXTRA BUTTON, NEVER WRAPS) */}
              <div className="px-5 py-2 shrink-0">
                <div className="w-full h-11 flex items-center bg-[#F3EDF7] border border-[#E7E0EC] focus-within:border-[#6750A4] rounded-2xl px-3.5 gap-2.5 transition">
                  <Search className="w-4 h-4 text-[#6750A4] shrink-0" />
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Search by name or number"
                    className="bg-transparent text-sm text-[#1D1B20] placeholder-[#79747E] focus:outline-none w-full min-w-0 font-medium truncate"
                  />
                  {searchQuery && (
                    <button onClick={() => setSearchQuery('')} className="text-[#49454F] hover:text-[#1D1B20]">
                      <X className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </div>

              {/* Sliding 2-Pane Call Log Container */}
              <div
                className="flex-1 overflow-hidden relative"
                onTouchStart={(e) => {
                  touchStartXRef.current = e.touches[0].clientX;
                }}
                onTouchEnd={(e) => {
                  const diff = touchStartXRef.current - e.changedTouches[0].clientX;
                  if (diff > 50 && activeCategoryFilter === 'ALL') {
                    setActiveCategoryFilter(3);
                  } else if (diff < -50 && activeCategoryFilter === 3) {
                    setActiveCategoryFilter('ALL');
                  }
                }}
              >
                <div
                  className="flex h-full w-[200%] transition-transform duration-300 ease-[cubic-bezier(0.2,0,0,1)]"
                  style={{
                    transform: activeCategoryFilter === 'ALL' ? 'translateX(0%)' : 'translateX(-50%)',
                  }}
                >
                  {/* Pane 1: All Groups */}
                  <div className="w-1/2 h-full overflow-y-auto divide-y divide-[#E7E0EC]/70 px-4 pb-24">
                    {allFilteredGroups.length === 0 ? (
                      <div className="py-20 text-center text-sm text-[#79747E] font-medium">
                        No calls found
                      </div>
                    ) : (
                      allFilteredGroups.map((group) => {
                        const latest = group.latestItem;
                        const typeMeta = CALL_TYPES.find((c) => c.type === latest.type) || CALL_TYPES[0];
                        const Icon = typeMeta.icon;
                        const isMissed = latest.type === 3;
                        const isSelected = isGroupSelected(group);

                        return (
                          <div
                            key={group.key}
                            onClick={() => {
                              if (isSelectionMode) {
                                toggleGroupSelect(group);
                              } else {
                                // Google Phone Standard: Open Call History Sheet
                                setViewingGroup(group);
                              }
                            }}
                            onContextMenu={(e) => {
                              e.preventDefault();
                              if (!isSelectionMode) {
                                setIsSelectionMode(true);
                                setSelectedIds(new Set(group.calls.map((c) => c.id)));
                              }
                            }}
                            className={`px-3 py-3 flex items-center justify-between gap-3 rounded-2xl cursor-pointer transition select-none ${
                              isSelected
                                ? 'bg-[#E8DEF8]/50 border border-[#6750A4]'
                                : 'hover:bg-[#F3EDF7]'
                            }`}
                          >
                            <div className="flex items-center gap-3 min-w-0 flex-1">
                              {isSelectionMode && (
                                <div
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    toggleGroupSelect(group);
                                  }}
                                  className={`w-5 h-5 rounded-full border-2 flex items-center justify-center transition shrink-0 ${
                                    isSelected
                                      ? 'bg-[#6750A4] border-[#6750A4] text-white shadow-xs'
                                      : 'border-[#79747E]'
                                  }`}
                                >
                                  {isSelected && <Check className="w-3.5 h-3.5 stroke-[3]" />}
                                </div>
                              )}

                              <div
                                className={`w-9 h-9 rounded-2xl flex items-center justify-center shrink-0 ${typeMeta.bgLight}`}
                              >
                                <Icon className={`w-4 h-4 ${typeMeta.iconColor} stroke-[2.2]`} />
                              </div>

                              <div className="min-w-0 flex-1">
                                <div className="flex items-center gap-1.5">
                                  <p
                                    className={`text-sm font-bold truncate ${
                                      isMissed ? 'text-[#F43F5E]' : 'text-[#1D1B20]'
                                    }`}
                                  >
                                    {group.name}
                                  </p>

                                  {/* Official Google Phone Red Alert ONLY on consecutive missed calls */}
                                  {group.consecutiveMissedCount >= 2 && (
                                    <span className="text-xs font-black text-[#F43F5E] shrink-0">
                                      ({group.consecutiveMissedCount})
                                    </span>
                                  )}
                                </div>

                                <div className="flex items-center gap-1.5 mt-0.5">
                                  <span
                                    className={`text-[9px] font-black px-1.5 py-0.5 rounded-md leading-none ${
                                      (latest.simSlot || 1) === 1
                                        ? 'bg-[#E8DEF8] text-[#6750A4]'
                                        : 'bg-[#E0F2FE] text-[#0284C7]'
                                    }`}
                                  >
                                    SIM {latest.simSlot || 1}
                                  </span>

                                  <p className="text-xs text-[#49454F] truncate font-medium">
                                    {group.number} {latest.durationSeconds > 0 && `• ${formatDuration(latest.durationSeconds)}`}
                                  </p>
                                </div>
                              </div>
                            </div>

                            {/* Right: Time ONLY (NO TRASH, NO CHEVRON - CLEAN!) */}
                            <span className="text-xs text-[#79747E] font-semibold shrink-0">
                              {formatCallTime(latest.date)}
                            </span>
                          </div>
                        );
                      })
                    )}
                  </div>

                  {/* Pane 2: Missed Groups */}
                  <div className="w-1/2 h-full overflow-y-auto divide-y divide-[#E7E0EC]/70 px-4 pb-24">
                    {missedFilteredGroups.length === 0 ? (
                      <div className="py-20 text-center text-sm text-[#79747E] font-medium">
                        No missed calls found
                      </div>
                    ) : (
                      missedFilteredGroups.map((group) => {
                        const latest = group.latestItem;
                        const typeMeta = CALL_TYPES.find((c) => c.type === latest.type) || CALL_TYPES[2];
                        const Icon = typeMeta.icon;
                        const isSelected = isGroupSelected(group);

                        return (
                          <div
                            key={group.key}
                            onClick={() => {
                              if (isSelectionMode) toggleGroupSelect(group);
                              else setViewingGroup(group);
                            }}
                            onContextMenu={(e) => {
                              e.preventDefault();
                              if (!isSelectionMode) {
                                setIsSelectionMode(true);
                                setSelectedIds(new Set(group.calls.map((c) => c.id)));
                              }
                            }}
                            className={`px-3 py-3 flex items-center justify-between gap-3 rounded-2xl cursor-pointer transition select-none ${
                              isSelected ? 'bg-[#E8DEF8]/50 border border-[#6750A4]' : 'hover:bg-[#F3EDF7]'
                            }`}
                          >
                            <div className="flex items-center gap-3 min-w-0 flex-1">
                              {isSelectionMode && (
                                <div
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    toggleGroupSelect(group);
                                  }}
                                  className={`w-5 h-5 rounded-full border-2 flex items-center justify-center transition shrink-0 ${
                                  isSelected ? 'bg-[#6750A4] border-[#6750A4] text-white shadow-xs' : 'border-[#79747E]'
                                }`}>
                                  {isSelected && <Check className="w-3.5 h-3.5 stroke-[3]" />}
                                </div>
                              )}

                              <div className={`w-9 h-9 rounded-2xl flex items-center justify-center shrink-0 ${typeMeta.bgLight}`}>
                                <Icon className={`w-4 h-4 ${typeMeta.iconColor} stroke-[2.2]`} />
                              </div>

                              <div className="min-w-0 flex-1">
                                <div className="flex items-center gap-1.5">
                                  <p className="text-sm font-bold text-[#F43F5E] truncate">{group.name}</p>
                                  {group.consecutiveMissedCount >= 2 && (
                                    <span className="text-xs font-black text-[#F43F5E]">({group.consecutiveMissedCount})</span>
                                  )}
                                </div>
                                <div className="flex items-center gap-1.5 mt-0.5">
                                  <span className="text-[9px] font-black px-1.5 py-0.5 rounded-md leading-none bg-[#E8DEF8] text-[#6750A4]">
                                    SIM {latest.simSlot || 1}
                                  </span>
                                  <p className="text-xs text-[#49454F] truncate font-medium">{group.number}</p>
                                </div>
                              </div>
                            </div>

                            <span className="text-xs text-[#79747E] font-semibold shrink-0">
                              {formatCallTime(latest.date)}
                            </span>
                          </div>
                        );
                      })
                    )}
                  </div>
                </div>
              </div>

              {/* Material 3 FAB */}
              {!isSelectionMode && (
                <button
                  onClick={() => openAddModal()}
                  className="absolute bottom-10 right-6 w-14 h-14 bg-[#6750A4] hover:bg-[#553F8D] text-white rounded-2xl shadow-xl flex items-center justify-center transition-all transform active:scale-95 shadow-[#6750A4]/35 z-10"
                >
                  <Plus className="w-6 h-6 stroke-[2.5]" />
                </button>
              )}
            </div>

            {/* SCREEN 2: SETTINGS */}
            <div className="w-full h-full shrink-0 flex flex-col overflow-y-auto px-4 py-4 space-y-4">
              {/* Header */}
              <div className="flex items-center gap-3 pb-2 border-b border-[#E7E0EC] shrink-0">
                <button
                  onClick={() => setCurrentScreen('recents')}
                  className="p-2 -ml-2 rounded-full hover:bg-black/5 text-[#1D1B20] transition"
                >
                  <ArrowLeft className="w-5 h-5" />
                </button>
                <div>
                  <h2 className="text-lg font-black text-[#1D1B20]">Settings</h2>
                  <p className="text-[11px] text-[#6750A4] font-semibold">Preferences, System & Diagnostics</p>
                </div>
              </div>


              {/* SECTION 1: PERMISSIONS & SYSTEM DIAGNOSTICS */}
              <div className="space-y-2">
                <span className="text-[11px] font-bold text-[#6750A4] uppercase tracking-wider px-1 block">
                  System & Permissions
                </span>

                <div className="bg-[#FEF7FF] rounded-[20px] p-4 border border-[#E7E0EC] shadow-xs space-y-3">
                  {/* Item 1: Dialer Role */}
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <ShieldCheck className={`w-5 h-5 shrink-0 ${isDialerRoleGranted ? 'text-[#10B981]' : 'text-[#6750A4]'}`} />
                      <span className="text-sm font-semibold text-[#1D1B20]">Default Dialer Role</span>
                    </div>

                    <button
                      type="button"
                      onClick={() => setIsDialerRoleGranted(!isDialerRoleGranted)}
                      style={{ width: '44px', height: '26px' }}
                      className={`relative inline-flex shrink-0 items-center rounded-full p-0.5 transition-colors duration-300 ${
                        isDialerRoleGranted ? 'bg-[#6750A4]' : 'bg-[#ECE6F0] border border-[#E7E0EC]'
                      }`}
                    >
                      <span
                        className={`pointer-events-none inline-flex h-5 w-5 transform items-center justify-center rounded-full bg-white shadow-md transition duration-300 ${
                          isDialerRoleGranted ? 'translate-x-4' : 'translate-x-0'
                        }`}
                      >
                        {isDialerRoleGranted && <Check className="w-3 h-3 text-[#6750A4] stroke-[3]" />}
                      </span>
                    </button>
                  </div>

                  <div className="h-px bg-[#E7E0EC]/50" />

                  {/* Item 2: Call Log Access */}
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-xs font-medium text-[#1D1B20]">Call Logs Access</span>
                    {hasCallLogPermission ? (
                      <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md bg-[#10B981]/15 text-[#10B981] text-[10px] font-bold">
                        <span className="w-1.5 h-1.5 rounded-full bg-[#10B981]" /> Granted
                      </span>
                    ) : (
                      <button
                        onClick={() => setHasCallLogPermission(true)}
                        className="px-2 py-0.5 rounded-md bg-[#F59E0B] text-white text-[10px] font-bold"
                      >
                        Grant
                      </button>
                    )}
                  </div>

                  {/* Item 3: Contacts Access */}
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-xs font-medium text-[#1D1B20]">Contacts Access</span>
                    {hasContactsPermission ? (
                      <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md bg-[#10B981]/15 text-[#10B981] text-[10px] font-bold">
                        <span className="w-1.5 h-1.5 rounded-full bg-[#10B981]" /> Granted
                      </span>
                    ) : (
                      <button
                        onClick={() => setHasContactsPermission(true)}
                        className="px-2 py-0.5 rounded-md bg-[#F59E0B] text-white text-[10px] font-bold"
                      >
                        Grant
                      </button>
                    )}
                  </div>

                  {/* Item 4: Phone State Access */}
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-xs font-medium text-[#1D1B20]">Phone State Access</span>
                    {hasPhoneStatePermission ? (
                      <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md bg-[#10B981]/15 text-[#10B981] text-[10px] font-bold">
                        <span className="w-1.5 h-1.5 rounded-full bg-[#10B981]" /> Granted
                      </span>
                    ) : (
                      <button
                        onClick={() => setHasPhoneStatePermission(true)}
                        className="px-2 py-0.5 rounded-md bg-[#F59E0B] text-white text-[10px] font-bold"
                      >
                        Grant
                      </button>
                    )}
                  </div>

                  <button
                    onClick={() => triggerCopy('Settings', 'Opened System Settings')}
                    className="w-full text-center text-xs font-semibold text-[#6750A4] pt-1 hover:underline transition"
                  >
                    Open System App Settings
                  </button>
                </div>
              </div>

              {/* SECTION 2: DEVELOPER & COMMUNITY */}
              <div className="space-y-2">
                <span className="text-[11px] font-bold text-[#6750A4] uppercase tracking-wider px-1 block">
                  Developer & Community
                </span>

                <div className="bg-[#FEF7FF] rounded-[20px] p-3.5 border border-[#E7E0EC] shadow-xs space-y-2.5">
                  {/* Sachin Mandawi Profile (Real Avatar) */}
                  <a
                    href="https://github.com/sachinmandawi"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center justify-between gap-3 group text-left hover:opacity-90 transition py-1"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <img
                        src="/sachin_avatar.png"
                        alt="Sachin Mandawi"
                        className="w-8 h-8 rounded-full object-cover border border-[#E7E0EC] shrink-0"
                      />
                      <div className="min-w-0">
                        <div className="text-xs font-bold text-[#1D1B20]">Sachin Mandawi</div>
                        <div className="text-[10px] text-[#79747E]">GitHub Profile</div>
                      </div>
                    </div>
                    <ExternalLink className="w-4 h-4 text-[#79747E] group-hover:text-[#6750A4] transition shrink-0" />
                  </a>

                  <div className="h-px bg-[#E7E0EC]/50" />

                  {/* CallCraft Repository (Real CallCraft Logo) */}
                  <a
                    href="https://github.com/sachinmandawi/Call-Craft"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center justify-between gap-3 group text-left hover:opacity-90 transition py-1"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <img
                        src="/logo.png"
                        alt="CallCraft"
                        className="w-8 h-8 rounded-lg object-cover border border-[#E7E0EC] shrink-0"
                      />
                      <div className="min-w-0">
                        <div className="text-xs font-bold text-[#1D1B20]">CallCraft Repository</div>
                        <div className="text-[10px] text-[#79747E]">Source code & releases</div>
                      </div>
                    </div>
                    <ExternalLink className="w-4 h-4 text-[#79747E] group-hover:text-[#6750A4] transition shrink-0" />
                  </a>

                  <div className="h-px bg-[#E7E0EC]/50" />

                  {/* Contact Developer (Official Gmail Icon) */}
                  <div
                    onClick={() => triggerCopy('Email', 'sachinmandawi@gmail.com')}
                    className="flex items-center justify-between gap-3 group text-left hover:opacity-90 transition py-1 cursor-pointer"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-8 h-8 rounded-lg bg-white border border-[#E7E0EC] flex items-center justify-center shrink-0 p-1">
                        <img src="/gmail_logo.svg" alt="Gmail" className="w-4 h-4 object-contain" />
                      </div>
                      <div className="min-w-0">
                        <div className="text-xs font-bold text-[#1D1B20]">Contact Developer</div>
                        <div className="text-[10px] text-[#79747E]">sachinmandawi@gmail.com</div>
                      </div>
                    </div>
                    <div className="flex items-center gap-1 shrink-0">
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          triggerCopy('Email', 'sachinmandawi@gmail.com');
                        }}
                        className="p-1.5 rounded-lg text-[#6750A4] hover:bg-[#6750A4]/10 transition"
                        title="Copy Email"
                      >
                        <Copy className="w-4 h-4" />
                      </button>
                      <a
                        href="mailto:sachinmandawi@gmail.com?subject=CallCraft%20Inquiry"
                        onClick={(e) => e.stopPropagation()}
                        className="p-1.5 rounded-lg text-[#79747E] hover:text-[#6750A4] hover:bg-black/5 transition"
                        title="Send Email"
                      >
                        <ExternalLink className="w-4 h-4" />
                      </a>
                    </div>
                  </div>

                  <div className="h-px bg-[#E7E0EC]/50" />

                  {/* Report an Issue */}
                  <a
                    href="https://github.com/sachinmandawi/Call-Craft/issues/new"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center justify-between gap-3 group text-left hover:opacity-90 transition py-1"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-8 h-8 rounded-lg bg-[#F43F5E]/10 text-[#F43F5E] flex items-center justify-center shrink-0">
                        <Bug className="w-4 h-4" />
                      </div>
                      <div className="min-w-0">
                        <div className="text-xs font-bold text-[#1D1B20]">Report an Issue</div>
                        <div className="text-[10px] text-[#79747E]">GitHub Issues</div>
                      </div>
                    </div>
                    <ExternalLink className="w-4 h-4 text-[#79747E] group-hover:text-[#6750A4] transition shrink-0" />
                  </a>
                </div>
              </div>

              {/* SECTION 3: OTHER APPS (CleanBar with real logo) */}
              <div className="space-y-2">
                <span className="text-[11px] font-bold text-[#6750A4] uppercase tracking-wider px-1 block">
                  Other Apps
                </span>

                <a
                  href="https://github.com/sachinmandawi/CleanBar"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="block bg-[#FEF7FF] rounded-[20px] p-3.5 border border-[#E7E0EC] shadow-xs hover:border-[#6750A4]/40 transition group"
                >
                  <div className="flex items-center gap-3">
                    <img
                      src="/cleanbar_logo.png"
                      alt="CleanBar"
                      className="w-9 h-9 rounded-xl object-cover border border-[#E7E0EC] shrink-0"
                    />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-1.5">
                        <span className="text-xs font-bold text-[#1D1B20]">CleanBar</span>
                        <span className="px-1.5 py-0.5 rounded bg-[#E8DEF8] text-[#21005D] text-[9px] font-bold">
                          No Root
                        </span>
                      </div>
                      <p className="text-[11px] text-[#49454F] mt-0.5 truncate">
                        Status Bar Hider via Shizuku
                      </p>
                    </div>
                    <ExternalLink className="w-4 h-4 text-[#79747E] group-hover:text-[#6750A4] transition shrink-0" />
                  </div>
                </a>
              </div>

              {/* SECTION 4: SUPPORT DEVELOPER (App-Themed, Original Icons) */}
              <div className="space-y-2">
                <span className="text-[11px] font-bold text-[#6750A4] uppercase tracking-wider px-1 block">
                  Support Developer
                </span>

                {/* UPI Card */}
                <div
                  onClick={() => triggerCopy('UPI ID', 'darkcaptain@ybl')}
                  className="bg-[#FEF7FF] rounded-[18px] p-3 border border-[#E7E0EC] flex items-center justify-between cursor-pointer hover:border-[#6750A4]/40 transition shadow-xs group"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="w-9 h-9 rounded-xl bg-white border border-[#E7E0EC] flex items-center justify-center shrink-0 p-1">
                      <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none">
                        <path d="M5 4.5L12.5 12L5 19.5H8.5L16 12L8.5 4.5H5Z" fill="#097939" />
                        <path d="M11.5 4.5L19 12L11.5 19.5H15L22.5 12L15 4.5H11.5Z" fill="#F37021" />
                      </svg>
                    </div>
                    <div className="min-w-0">
                      <div className="text-[10px] font-bold text-[#79747E] uppercase tracking-wider">UPI</div>
                      <div className="text-xs font-bold text-[#1D1B20] font-mono truncate">darkcaptain@ybl</div>
                    </div>
                  </div>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      triggerCopy('UPI ID', 'darkcaptain@ybl');
                    }}
                    className="p-1.5 rounded-lg text-[#6750A4] hover:bg-[#6750A4]/10 transition shrink-0"
                    title="Copy UPI ID"
                  >
                    <Copy className="w-4 h-4" />
                  </button>
                </div>

                {/* Binance Card */}
                <div
                  onClick={() => triggerCopy('Binance ID', '1138545342')}
                  className="bg-[#FEF7FF] rounded-[18px] p-3 border border-[#E7E0EC] flex items-center justify-between cursor-pointer hover:border-[#6750A4]/40 transition shadow-xs group"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="w-9 h-9 rounded-xl bg-[#F0B90B]/15 border border-[#F0B90B]/30 flex items-center justify-center shrink-0 p-1">
                      <svg viewBox="0 0 24 24" className="w-5 h-5" fill="#F0B90B">
                        <path d="M12 9.36L14.64 12L12 14.64L9.36 12L12 9.36ZM12 2L16.62 6.62L13.91 9.34L12 7.43L10.09 9.34L7.38 6.62L12 2ZM12 22L7.38 17.38L10.09 14.66L12 16.57L13.91 14.66L16.62 17.38L12 22ZM2 12L4.72 9.28L7.43 12L4.72 14.72L2 12ZM22 12L19.28 9.28L16.57 12L19.28 14.72L22 12Z" />
                      </svg>
                    </div>
                    <div className="min-w-0">
                      <div className="text-[10px] font-bold text-[#79747E] uppercase tracking-wider">Binance Pay</div>
                      <div className="text-xs font-bold text-[#1D1B20] font-mono truncate">1138545342</div>
                    </div>
                  </div>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      triggerCopy('Binance ID', '1138545342');
                    }}
                    className="p-1.5 rounded-lg text-[#6750A4] hover:bg-[#6750A4]/10 transition shrink-0"
                    title="Copy Binance ID"
                  >
                    <Copy className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {/* SECTION 5: APP VERSION & IN-PLACE UPDATER */}
              <div className="space-y-2">
                <span className="text-[11px] font-bold text-[#6750A4] uppercase tracking-wider px-1 block">
                  About & Updates
                </span>

                <div className="bg-[#FEF7FF] rounded-[20px] p-3.5 border border-[#E7E0EC] shadow-xs space-y-2.5">
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-2.5">
                      <img src="/logo.png" alt="CallCraft" className="w-7 h-7 rounded-lg border border-[#E7E0EC]" />
                      <div>
                        <div className="text-xs font-black text-[#1D1B20]">
                          Call<span className="text-[#6750A4]">Craft</span>
                        </div>
                        <div className="text-[10px] text-[#79747E] font-medium">Version 1.0.0</div>
                      </div>
                    </div>

                    <button
                      onClick={checkGithubUpdates}
                      disabled={updateStatus === 'checking'}
                      className="px-3 py-1 rounded-lg bg-[#6750A4] hover:bg-[#553F8D] text-white text-xs font-bold flex items-center gap-1.5 transition disabled:opacity-60 shadow-xs"
                    >
                      <RefreshCw className={`w-3 h-3 ${updateStatus === 'checking' ? 'animate-spin' : ''}`} />
                      <span>{updateStatus === 'checking' ? 'Checking...' : 'Check Update'}</span>
                    </button>
                  </div>

                  {updateStatus === 'latest' && (
                    <div className="px-2.5 py-1.5 rounded-lg bg-[#10B981]/10 text-[#10B981] flex items-center gap-2">
                      <Check className="w-3.5 h-3.5 shrink-0" />
                      <span className="text-xs font-bold">v1.0.0 is the latest version</span>
                    </div>
                  )}

                  {updateStatus === 'available' && latestReleaseInfo && (
                    <div className="p-2.5 rounded-xl bg-[#E8DEF8] border border-[#6750A4]/30 space-y-1.5">
                      <div className="text-xs font-bold text-[#21005D]">
                        Update Available: v{latestReleaseInfo.version}
                      </div>
                      <a
                        href={latestReleaseInfo.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center justify-center gap-1.5 w-full py-1.5 rounded-lg bg-[#6750A4] text-white text-xs font-bold hover:bg-[#553F8D] transition"
                      >
                        <Download className="w-3 h-3" />
                        <span>Install Update (In-Place)</span>
                      </a>
                    </div>
                  )}

                  {updateStatus === 'error' && (
                    <div className="px-2.5 py-1.5 rounded-lg bg-[#F43F5E]/10 text-[#F43F5E] text-xs font-medium">
                      Failed to check update.
                    </div>
                  )}
                </div>
              </div>

              <div className="h-6" />
            </div>
          </div>
        </div>

        {/* Google Phone Standard: Contact Call History Bottom Sheet */}
        {viewingGroup && (
          <div className="absolute inset-0 bg-black/50 backdrop-blur-xs flex items-end justify-center z-50 animate-in fade-in duration-150">
            <div className="bg-[#FEF7FF] text-[#1D1B20] w-full rounded-t-[32px] px-5 pt-3 pb-6 shadow-2xl flex flex-col max-h-[88%] border-t border-[#E7E0EC] animate-in slide-in-from-bottom duration-200">
              {/* Drag Handle */}
              <div className="w-10 h-1 bg-[#79747E]/30 rounded-full mx-auto shrink-0 mb-3" />

              {/* Contact Monogram & Details */}
              <div className="flex items-center justify-between mb-3 shrink-0">
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-11 h-11 rounded-full bg-gradient-to-tr from-[#6750A4] to-[#4F378B] text-white flex items-center justify-center text-base font-black shadow-sm shrink-0">
                    {viewingGroup.name.slice(0, 1).toUpperCase()}
                  </div>
                  <div className="min-w-0 flex-1">
                    <h3 className="text-base font-black text-[#1D1B20] leading-tight truncate">{viewingGroup.name}</h3>
                    <p className="text-xs text-[#79747E] font-medium font-mono truncate">{viewingGroup.number}</p>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => setViewingGroup(null)}
                  className="w-8 h-8 rounded-full bg-[#ECE6F0] flex items-center justify-center text-[#49454F] hover:bg-[#E8DEF8] transition shrink-0 ml-2"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* Quick Actions: + Log Call & Delete All */}
              <div className="grid grid-cols-2 gap-2 mb-3 shrink-0">
                <button
                  type="button"
                  onClick={() => {
                    const target = viewingGroup;
                    setViewingGroup(null);
                    openAddModal({ name: target.name, number: target.number, simSlot: target.latestItem.simSlot });
                  }}
                  className="py-2.5 rounded-xl bg-[#6750A4] text-white text-xs font-bold flex items-center justify-center gap-1.5 shadow-xs hover:bg-[#553F8D] transition"
                >
                  <Plus className="w-4 h-4" />
                  <span>Log Call</span>
                </button>

                <button
                  type="button"
                  onClick={() => handleDeleteAllForGroup(viewingGroup)}
                  className="py-2.5 rounded-xl bg-rose-50 text-[#F43F5E] border border-rose-200 text-xs font-bold flex items-center justify-center gap-1.5 hover:bg-rose-100 transition"
                >
                  <Trash2 className="w-4 h-4" />
                  <span>Delete All</span>
                </button>
              </div>

              {/* Section Header */}
              <div className="mb-2 shrink-0">
                <span className="text-[10px] font-bold text-[#6750A4] uppercase tracking-wider block">
                  Call History ({viewingGroup.calls.length})
                </span>
              </div>

              {/* Smooth Scrollable Call History Cards (No cutting off, no overlap) */}
              <div className="flex-1 overflow-y-auto no-scrollbar space-y-2 pr-0.5 min-h-0">
                {viewingGroup.calls.map((call) => {
                  const typeMeta = CALL_TYPES.find((c) => c.type === call.type) || CALL_TYPES[0];
                  const Icon = typeMeta.icon;

                  return (
                    <div
                      key={call.id}
                      className="bg-[#F3EDF7] rounded-2xl p-3 flex items-center justify-between gap-2.5 hover:bg-[#ECE6F0] transition group border border-transparent hover:border-[#E7E0EC]"
                    >
                      <div className="flex items-center gap-2.5 min-w-0 flex-1">
                        <div className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 ${typeMeta.bgLight}`}>
                          <Icon className={`w-4 h-4 ${typeMeta.iconColor} stroke-[2.2]`} />
                        </div>

                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <span className="text-xs font-bold text-[#1D1B20]">{typeMeta.label}</span>
                            <span className={`text-[8px] font-black px-1.5 py-0.5 rounded-md ${
                              call.simSlot === 1 ? 'bg-[#E8DEF8] text-[#6750A4]' : 'bg-[#E0F2FE] text-[#0284C7]'
                            }`}>
                              SIM {call.simSlot}
                            </span>
                          </div>
                          <p className="text-[11px] text-[#49454F] font-medium truncate mt-0.5">
                            {formatFullDateTime(call.date)} {call.durationSeconds > 0 && `• ${formatDuration(call.durationSeconds)}`}
                          </p>
                        </div>
                      </div>

                      <div className="flex items-center gap-1 shrink-0 ml-1">
                        <button
                          type="button"
                          onClick={() => {
                            setViewingGroup(null);
                            openEditModal(call);
                          }}
                          className="p-1.5 rounded-full hover:bg-purple-100 text-[#6750A4] transition"
                          title="Edit"
                        >
                          <Edit3 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setLogs((prev) => prev.filter((item) => item.id !== call.id));
                          }}
                          className="p-1.5 rounded-full hover:bg-rose-100 text-[#F43F5E] transition"
                          title="Delete"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  );
                })}
                {/* Generous bottom spacing so the last card is never cut off or touching the phone bezel */}
                <div className="h-6 shrink-0" />
              </div>
            </div>
          </div>
        )}

        {/* Modal Bottom Sheet for Add / Edit */}
        {isModalOpen && (
          <div className="absolute inset-0 bg-black/50 backdrop-blur-xs flex items-end justify-center z-50 animate-in fade-in duration-150">
            <form
              onSubmit={handleSave}
              className="bg-[#FEF7FF] text-[#1D1B20] w-full rounded-t-[36px] p-6 shadow-2xl flex flex-col space-y-4 max-h-[92%] overflow-y-auto border-t border-[#E7E0EC]"
            >
              <div className="w-12 h-1 bg-[#79747E]/40 rounded-full mx-auto shrink-0 mb-1" />

              <div className="flex items-center justify-between">
                <h3 className="text-lg font-black text-[#1D1B20]">
                  {editingItem ? 'Edit Call' : 'New Call'}
                </h3>
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="w-8 h-8 rounded-full bg-[#ECE6F0] flex items-center justify-center text-[#49454F] hover:bg-[#E8DEF8] transition"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* Hardware Aware SIM Selector */}
              <div>
                <label className="block text-xs font-bold text-[#49454F] mb-1">
                  SIM Card
                </label>
                <div className="grid grid-cols-2 gap-2 bg-[#ECE6F0] p-1 rounded-2xl">
                  <button
                    type="button"
                    disabled={!hasSim1}
                    onClick={() => setFormSimSlot(1)}
                    className={`py-2 rounded-xl text-xs font-bold transition flex items-center justify-center gap-1.5 ${
                      !hasSim1
                        ? 'opacity-40 cursor-not-allowed text-[#79747E]'
                        : formSimSlot === 1
                        ? 'bg-[#6750A4] text-white shadow-xs'
                        : 'text-[#49454F] hover:text-[#1D1B20]'
                    }`}
                  >
                    <Smartphone className="w-3.5 h-3.5" />
                    <span>{hasSim1 ? 'SIM 1' : 'SIM 1 (No SIM)'}</span>
                  </button>
                  <button
                    type="button"
                    disabled={!hasSim2}
                    onClick={() => setFormSimSlot(2)}
                    className={`py-2 rounded-xl text-xs font-bold transition flex items-center justify-center gap-1.5 ${
                      !hasSim2
                        ? 'opacity-40 cursor-not-allowed text-[#79747E]'
                        : formSimSlot === 2
                        ? 'bg-[#0284C7] text-white shadow-xs'
                        : 'text-[#49454F] hover:text-[#1D1B20]'
                    }`}
                  >
                    <Smartphone className="w-3.5 h-3.5" />
                    <span>{hasSim2 ? 'SIM 2' : 'SIM 2 (No SIM)'}</span>
                  </button>
                </div>
              </div>

              {/* Name */}
              <div>
                <label className="block text-xs font-bold text-[#49454F] mb-1">
                  Name
                </label>
                <input
                  type="text"
                  value={formName}
                  onChange={(e) => setFormName(e.target.value)}
                  placeholder="e.g., Rohan Verma"
                  className="w-full bg-[#F3EDF7] border border-[#79747E]/30 rounded-2xl px-3.5 py-2.5 text-sm font-semibold focus:outline-none focus:border-[#6750A4] focus:bg-[#FEF7FF] transition"
                />
              </div>

              {/* Phone Number with Contact Picker */}
              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="block text-xs font-bold text-[#49454F]">
                    Phone Number *
                  </label>
                  <button
                    type="button"
                    onClick={() => setIsContactPickerOpen(true)}
                    className="flex items-center gap-1.5 text-xs font-bold text-[#6750A4] bg-[#E8DEF8] px-2.5 py-0.5 rounded-full hover:bg-[#D0BCFF] transition"
                  >
                    <Smartphone className="w-3.5 h-3.5" />
                    <span>Pick Contact</span>
                  </button>
                </div>
                <input
                  type="text"
                  required
                  value={formNumber}
                  onChange={(e) => setFormNumber(e.target.value)}
                  placeholder="+91 98765 43210"
                  className="w-full bg-[#F3EDF7] border border-[#79747E]/30 rounded-2xl px-3.5 py-2.5 text-sm font-mono font-semibold focus:outline-none focus:border-[#6750A4] focus:bg-[#FEF7FF] transition"
                />
              </div>

              {/* Call Type 4-grid */}
              <div>
                <label className="block text-xs font-bold text-[#49454F] mb-1.5">
                  Call Type
                </label>
                <div className="grid grid-cols-4 gap-1.5 bg-[#ECE6F0] p-1.5 rounded-2xl">
                  {CALL_TYPES.map((t) => {
                    const isSelected = formType === t.type;
                    return (
                      <button
                        type="button"
                        key={t.type}
                        onClick={() => {
                          setFormType(t.type);
                          if (t.type !== 3 && t.type !== 5 && formDuration === 0) {
                            setFormDuration(60);
                            setDurationHours('0');
                            setDurationMins('1');
                            setDurationSecs('0');
                          }
                        }}
                        className={`py-2 rounded-xl text-xs font-bold transition flex flex-col items-center gap-1 ${
                          isSelected
                            ? 'bg-[#FEF7FF] shadow-sm'
                            : 'text-[#49454F] hover:text-[#1D1B20]'
                        }`}
                      >
                        <t.icon className={`w-4 h-4 ${isSelected ? t.iconColor : 'text-[#79747E]'}`} />
                        <span className={isSelected ? 'text-[#1D1B20]' : 'text-[#49454F]'}>
                          {t.label}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Duration Inputs */}
              {formType !== 3 && formType !== 5 && (
                <div className="bg-[#F3EDF7] p-3.5 rounded-2xl border border-[#E7E0EC] space-y-3">
                  <div className="flex justify-between items-center">
                    <div className="flex items-center gap-1.5">
                      <Clock className="w-4 h-4 text-[#6750A4]" />
                      <label className="text-xs font-bold text-[#1D1B20]">
                        Call Duration
                      </label>
                    </div>
                    <span className="text-xs font-black text-[#6750A4] bg-[#E8DEF8] px-2.5 py-0.5 rounded-full">
                      {formatDuration(formDuration) || '0s'}
                    </span>
                  </div>

                  <div className="grid grid-cols-3 gap-2 items-center">
                    <div className="bg-[#FEF7FF] rounded-xl px-3 py-2 border border-[#E7E0EC] focus-within:border-[#6750A4] flex items-center justify-between gap-1.5">
                      <input
                        type="text"
                        inputMode="numeric"
                        value={durationHours}
                        onChange={(e) => {
                          const val = e.target.value.replace(/\D/g, '');
                          setDurationHours(val);
                          setFormDuration(parseInt(val || '0') * 3600 + parseInt(durationMins || '0') * 60 + parseInt(durationSecs || '0'));
                        }}
                        placeholder="0"
                        className="w-full text-base font-black text-[#1D1B20] bg-transparent outline-none"
                      />
                      <span className="text-xs font-bold text-[#6750A4] bg-[#E8DEF8] px-2 py-1 rounded-lg shrink-0">hr</span>
                    </div>

                    <div className="bg-[#FEF7FF] rounded-xl px-3 py-2 border border-[#E7E0EC] focus-within:border-[#6750A4] flex items-center justify-between gap-1.5">
                      <input
                        type="text"
                        inputMode="numeric"
                        value={durationMins}
                        onChange={(e) => {
                          const val = e.target.value.replace(/\D/g, '');
                          setDurationMins(val);
                          setFormDuration(parseInt(durationHours || '0') * 3600 + parseInt(val || '0') * 60 + parseInt(durationSecs || '0'));
                        }}
                        placeholder="0"
                        className="w-full text-base font-black text-[#1D1B20] bg-transparent outline-none"
                      />
                      <span className="text-xs font-bold text-[#6750A4] bg-[#E8DEF8] px-2 py-1 rounded-lg shrink-0">min</span>
                    </div>

                    <div className="bg-[#FEF7FF] rounded-xl px-3 py-2 border border-[#E7E0EC] focus-within:border-[#6750A4] flex items-center justify-between gap-1.5">
                      <input
                        type="text"
                        inputMode="numeric"
                        value={durationSecs}
                        onChange={(e) => {
                          const val = e.target.value.replace(/\D/g, '');
                          setDurationSecs(val);
                          setFormDuration(parseInt(durationHours || '0') * 3600 + parseInt(durationMins || '0') * 60 + parseInt(val || '0'));
                        }}
                        placeholder="0"
                        className="w-full text-base font-black text-[#1D1B20] bg-transparent outline-none"
                      />
                      <span className="text-xs font-bold text-[#6750A4] bg-[#E8DEF8] px-2 py-1 rounded-lg shrink-0">sec</span>
                    </div>
                  </div>
                </div>
              )}

              {/* Actions */}
              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="flex-1 py-3 rounded-full text-xs font-bold text-[#6750A4] bg-[#ECE6F0] hover:bg-[#E8DEF8] transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 py-3 rounded-full text-xs font-bold bg-[#6750A4] text-white hover:bg-[#553F8D] shadow-md transition"
                >
                  Save Log
                </button>
              </div>
            </form>
          </div>
        )}

        {/* Bulk Actions Modal */}
        {showBulkActions && (
          <div className="absolute inset-0 bg-black/50 backdrop-blur-xs flex items-end justify-center z-50 animate-in fade-in duration-150">
            <div className="bg-[#FEF7FF] text-[#1D1B20] w-full rounded-t-[36px] p-6 shadow-2xl flex flex-col space-y-4 border-t border-[#E7E0EC]">
              <div className="w-12 h-1 bg-[#79747E]/40 rounded-full mx-auto shrink-0 mb-1" />

              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-base font-black text-[#1D1B20]">Bulk Actions</h3>
                  <p className="text-xs text-[#6750A4] font-semibold">{selectedIds.size} calls selected</p>
                </div>
                <button
                  onClick={() => setShowBulkActions(false)}
                  className="w-7 h-7 rounded-full bg-[#ECE6F0] flex items-center justify-center text-[#49454F]"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* Convert Call Type */}
              <div>
                <span className="text-[10px] font-bold text-[#79747E] uppercase tracking-wider block mb-2">
                  Convert Call Type
                </span>
                <div className="grid grid-cols-4 gap-2">
                  {CALL_TYPES.map((t) => (
                    <button
                      key={t.type}
                      onClick={() => handleBulkTypeChange(t.type)}
                      className={`p-2.5 rounded-xl text-xs font-bold ${t.bgLight} ${t.iconColor} hover:opacity-80 transition flex flex-col items-center gap-1`}
                    >
                      <t.icon className="w-4 h-4" />
                      <span>{t.label}</span>
                    </button>
                  ))}
                </div>
              </div>

              {/* Switch SIM Card */}
              <div>
                <span className="text-[10px] font-bold text-[#79747E] uppercase tracking-wider block mb-2">
                  Set SIM Card
                </span>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    disabled={!hasSim1}
                    onClick={() => hasSim1 && handleBulkSimChange(1)}
                    className={`p-3 rounded-xl text-xs font-bold flex items-center justify-center gap-2 transition ${
                      hasSim1 ? 'bg-[#E8DEF8] text-[#6750A4] hover:bg-[#D0BCFF]' : 'bg-[#ECE6F0] opacity-40 text-[#79747E] cursor-not-allowed'
                    }`}
                  >
                    <Smartphone className="w-4 h-4" />
                    <span>{hasSim1 ? 'Switch to SIM 1' : 'SIM 1 (No SIM)'}</span>
                  </button>
                  <button
                    disabled={!hasSim2}
                    onClick={() => hasSim2 && handleBulkSimChange(2)}
                    className={`p-3 rounded-xl text-xs font-bold flex items-center justify-center gap-2 transition ${
                      hasSim2 ? 'bg-[#E0F2FE] text-[#0284C7] hover:bg-[#BAE6FD]' : 'bg-[#ECE6F0] opacity-40 text-[#79747E] cursor-not-allowed'
                    }`}
                  >
                    <Smartphone className="w-4 h-4" />
                    <span>{hasSim2 ? 'Switch to SIM 2' : 'SIM 2 (No SIM)'}</span>
                  </button>
                </div>
              </div>

              <button
                onClick={() => setShowBulkActions(false)}
                className="w-full py-2.5 rounded-full text-xs font-bold text-[#6750A4] bg-[#ECE6F0] hover:bg-[#E8DEF8] transition mt-2"
              >
                Cancel
              </button>
            </div>
          </div>
        )}

        {/* Delete Confirmation Modal */}
        {showDeleteConfirm && (
          <div className="absolute inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center z-50 p-4 animate-in fade-in duration-150">
            <div className="bg-[#FEF7FF] text-[#1D1B20] w-full max-w-[320px] rounded-[32px] p-5 shadow-2xl border border-[#E7E0EC] flex flex-col space-y-3">
              <h3 className="text-base font-black text-[#1D1B20]">Delete Call Logs</h3>
              <p className="text-xs text-[#49454F] font-medium leading-relaxed">
                Are you sure you want to delete {selectedIds.size} selected call log(s)? This action cannot be undone.
              </p>
              <div className="flex gap-2 pt-2 justify-end">
                <button
                  onClick={() => setShowDeleteConfirm(false)}
                  className="px-4 py-2 text-xs font-bold text-[#6750A4] hover:bg-[#ECE6F0] rounded-full transition"
                >
                  Cancel
                </button>
                <button
                  onClick={handleBulkDeleteConfirm}
                  className="px-4 py-2 text-xs font-bold bg-[#F43F5E] text-white hover:bg-rose-600 rounded-full shadow-md transition"
                >
                  Delete
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Contact Picker Modal */}
        {isContactPickerOpen && (
          <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-[110] flex items-center justify-center p-4 animate-in fade-in duration-150">
            <div className="bg-[#FEF7FF] text-[#1D1B20] w-full max-w-[340px] rounded-[32px] p-5 shadow-2xl border border-[#E7E0EC] flex flex-col space-y-3 max-h-[85vh]">
              <div className="flex items-center justify-between pb-1">
                <div>
                  <h3 className="text-base font-black text-[#1D1B20]">Select Contact</h3>
                  <p className="text-[11px] text-[#6750A4] font-semibold">Phone Contacts</p>
                </div>
                <button
                  type="button"
                  onClick={() => setIsContactPickerOpen(false)}
                  className="w-7 h-7 rounded-full bg-[#ECE6F0] flex items-center justify-center text-[#49454F] hover:bg-[#E8DEF8]"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              <div className="flex items-center bg-[#F3EDF7] border border-[#E7E0EC] rounded-2xl px-3 py-2 gap-2">
                <Search className="w-3.5 h-3.5 text-[#6750A4] shrink-0" />
                <input
                  type="text"
                  value={contactSearch}
                  onChange={(e) => setContactSearch(e.target.value)}
                  placeholder="Search name or number"
                  className="bg-transparent text-xs text-[#1D1B20] placeholder-[#79747E] focus:outline-none w-full"
                />
              </div>

              <div className="overflow-y-auto space-y-1.5 pr-1 max-h-[280px]">
                {MOCK_CONTACTS.filter(
                  (c) =>
                    c.name.toLowerCase().includes(contactSearch.toLowerCase()) ||
                    c.number.includes(contactSearch)
                ).map((contact) => (
                  <div
                    key={contact.name}
                    onClick={() => {
                      setFormName(contact.name);
                      setFormNumber(contact.number);
                      setIsContactPickerOpen(false);
                      if (!isModalOpen) {
                        openAddModal({ name: contact.name, number: contact.number });
                      }
                    }}
                    className="flex items-center gap-3 p-2.5 rounded-2xl hover:bg-[#F3EDF7] cursor-pointer transition"
                  >
                    <div className={`w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold ${contact.color}`}>
                      {contact.initials}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="text-xs font-bold text-[#1D1B20] truncate">{contact.name}</div>
                      <div className="text-[11px] text-[#49454F] font-mono truncate">{contact.number}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
        {/* Fake Call Simulator Setup Modal */}
        {isFakeCallModalOpen && (
          <div className="absolute inset-0 bg-black/50 backdrop-blur-xs flex items-end justify-center z-50 animate-in fade-in duration-150">
            <div className="bg-[#FEF7FF] text-[#1D1B20] w-full rounded-t-[28px] p-5 shadow-2xl flex flex-col space-y-2.5 max-h-[92%] overflow-y-auto border-t border-[#E7E0EC]">
              <div className="w-10 h-1 bg-[#79747E]/40 rounded-full mx-auto shrink-0 mb-0.5" />

              {/* Header */}
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="w-[30px] h-[30px] rounded-[10px] bg-amber-500/15 text-amber-600 flex items-center justify-center shrink-0">
                    <Zap className="w-4 h-4 fill-amber-500" />
                  </div>
                  <div>
                    <h3 className="text-base font-black text-[#1D1B20] leading-tight">Fake Call Simulator</h3>
                    <p className="text-[10px] text-[#6750A4] font-semibold">Escape awkward situations safely</p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setIsFakeCallModalOpen(false)}
                  className="w-7 h-7 rounded-full bg-[#ECE6F0] flex items-center justify-center text-[#49454F] hover:bg-[#E8DEF8] transition"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* 1. Phone Brand / Theme Style Selector (2x2 Grid: 100% Visible, No Scroll Needed) */}
              <div>
                <label className="block text-[10px] font-bold text-[#79747E] tracking-wider uppercase mb-1.5">
                  Call Screen Brand / Theme
                </label>
                <div className="grid grid-cols-2 gap-2">
                  {[
                    { id: 'pixel', name: 'Google Pixel' },
                    { id: 'samsung', name: 'Samsung One UI' },
                    { id: 'hyperos', name: 'Xiaomi HyperOS' },
                    { id: 'ios', name: 'Apple iPhone' },
                  ].map((b) => {
                    const isSelected = fakeCallBrand === b.id;
                    return (
                      <button
                        key={b.id}
                        type="button"
                        onClick={() => setFakeCallBrand(b.id as any)}
                        className={`py-2 px-3 rounded-xl text-xs font-bold transition flex items-center justify-center border text-center ${
                          isSelected
                            ? 'bg-[#6750A4] text-white border-[#6750A4] shadow-sm'
                            : 'bg-[#ECE6F0] text-[#1D1B20] border-[#E7E0EC] hover:bg-[#E8DEF8]'
                        }`}
                      >
                        <span>{b.name}</span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* 2. Quick Caller Presets (Wrapped: 100% Visible) */}
              <div>
                <label className="block text-[10px] font-bold text-[#79747E] tracking-wider uppercase mb-1">
                  Quick Caller Preset
                </label>
                <div className="flex flex-wrap gap-1.5">
                  {FAKE_CALL_PRESETS.map((preset) => {
                    const isSelected = fakeCallName === preset.name;
                    return (
                      <button
                        key={preset.name}
                        type="button"
                        onClick={() => {
                          setFakeCallName(preset.name);
                          setFakeCallNumber(preset.number);
                        }}
                        className={`px-3 py-1.5 rounded-[10px] text-[11px] font-bold transition flex items-center border ${
                          isSelected
                            ? 'bg-[#6750A4] text-white border-[#6750A4] shadow-xs'
                            : 'bg-[#ECE6F0] text-[#1D1B20] border-[#E7E0EC] hover:bg-[#E8DEF8]'
                        }`}
                      >
                        <span>{preset.name}</span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* 3. Compact Caller Name */}
              <div className="space-y-0.5">
                <label className="block text-[11px] font-bold text-[#79747E]">
                  Caller Name
                </label>
                <div className="h-[42px] px-3 rounded-xl bg-[#F3EDF7] border border-[#E7E0EC] flex items-center focus-within:border-[#6750A4] focus-within:bg-[#FEF7FF] transition">
                  <input
                    type="text"
                    value={fakeCallName}
                    onChange={(e) => setFakeCallName(e.target.value)}
                    placeholder="e.g. Boss, Mom, Doctor"
                    className="w-full bg-transparent text-[13px] font-semibold text-[#1D1B20] outline-none placeholder:text-[#79747E]/60"
                  />
                </div>
              </div>

              {/* 4. Compact Phone Number with Contact Picker button */}
              <div className="space-y-0.5">
                <div className="flex items-center justify-between">
                  <label className="block text-[11px] font-bold text-[#79747E]">
                    Phone Number
                  </label>
                  <button
                    type="button"
                    onClick={() => setIsContactPickerOpen(true)}
                    className="flex items-center gap-1 text-[10px] font-bold text-[#6750A4] bg-[#E8DEF8] px-2 py-0.5 rounded-lg hover:bg-[#D0BCFF] transition"
                  >
                    <Smartphone className="w-3 h-3" />
                    <span>Pick Contact</span>
                  </button>
                </div>
                <div className="h-[42px] px-3 rounded-xl bg-[#F3EDF7] border border-[#E7E0EC] flex items-center focus-within:border-[#6750A4] focus-within:bg-[#FEF7FF] transition">
                  <input
                    type="text"
                    value={fakeCallNumber}
                    onChange={(e) => setFakeCallNumber(e.target.value)}
                    placeholder="+91 98765 43210"
                    className="w-full bg-transparent text-[13px] font-mono font-semibold text-[#1D1B20] outline-none placeholder:text-[#79747E]/60"
                  />
                </div>
              </div>

              {/* 5. Compact SIM Slot Selector */}
              <div className="space-y-0.5">
                <label className="block text-[11px] font-bold text-[#79747E]">
                  Incoming SIM Card
                </label>
                <div className="flex gap-1.5 bg-[#ECE6F0] p-1 rounded-xl">
                  <button
                    type="button"
                    disabled={!hasSim1}
                    onClick={() => setFakeCallSimSlot(1)}
                    className={`flex-1 py-1.5 rounded-[10px] text-[11px] font-bold transition flex items-center justify-center gap-1.5 ${
                      !hasSim1
                        ? 'opacity-40 cursor-not-allowed text-[#79747E]'
                        : fakeCallSimSlot === 1
                        ? 'bg-[#6750A4] text-white shadow-xs'
                        : 'text-[#49454F] hover:text-[#1D1B20]'
                    }`}
                  >
                    <Smartphone className="w-3.5 h-3.5" />
                    <span>{hasSim1 ? 'SIM 1 (Jio 5G)' : 'SIM 1 (No SIM)'}</span>
                  </button>
                  <button
                    type="button"
                    disabled={!hasSim2}
                    onClick={() => setFakeCallSimSlot(2)}
                    className={`flex-1 py-1.5 rounded-[10px] text-[11px] font-bold transition flex items-center justify-center gap-1.5 ${
                      !hasSim2
                        ? 'opacity-40 cursor-not-allowed text-[#79747E]'
                        : fakeCallSimSlot === 2
                        ? 'bg-[#0284C7] text-white shadow-xs'
                        : 'text-[#49454F] hover:text-[#1D1B20]'
                    }`}
                  >
                    <Smartphone className="w-3.5 h-3.5" />
                    <span>{hasSim2 ? 'SIM 2 (Airtel)' : 'SIM 2 (No SIM)'}</span>
                  </button>
                </div>
              </div>

              {/* 6. Compact Ring Delay (Presets + Custom Inputs) */}
              <div className="space-y-1">
                <div className="flex items-center justify-between">
                  <label className="text-[10px] font-bold text-[#79747E] tracking-wider uppercase">
                    Trigger Delay
                  </label>
                  <span className="text-[11px] font-bold text-[#6750A4]">
                    {fakeCallDelaySec <= 3
                      ? 'Ringing in 3s'
                      : `Ringing in ${Math.floor(fakeCallDelaySec / 60) > 0 ? `${Math.floor(fakeCallDelaySec / 60)}m ` : ''}${fakeCallDelaySec % 60}s`}
                  </span>
                </div>

                {/* Quick Presets */}
                <div className="flex gap-1.5 overflow-x-auto pb-1 no-scrollbar">
                  {[
                    { label: 'Now (3s)', sec: 3 },
                    { label: '10s', sec: 10 },
                    { label: '30s', sec: 30 },
                    { label: '1m', sec: 60 },
                    { label: '5m', sec: 300 },
                  ].map((preset) => (
                    <button
                      key={preset.sec}
                      type="button"
                      onClick={() => {
                        setFakeCallDelaySec(preset.sec);
                        setFakeCallMinsInput(Math.floor(preset.sec / 60).toString());
                        setFakeCallSecsInput((preset.sec % 60).toString());
                      }}
                      className={`px-2.5 py-1 rounded-[10px] text-[11px] font-bold shrink-0 transition border ${
                        fakeCallDelaySec === preset.sec
                          ? 'bg-[#6750A4] text-white border-[#6750A4] shadow-xs'
                          : 'bg-[#ECE6F0] text-[#49454F] border-[#E7E0EC] hover:bg-[#E8DEF8]'
                      }`}
                    >
                      {preset.label}
                    </button>
                  ))}
                </div>

                {/* Custom Min / Sec Inputs */}
                <div className="grid grid-cols-2 gap-2 bg-[#F3EDF7] p-2 rounded-xl border border-[#E7E0EC]">
                  <div>
                    <label className="block text-[10px] font-bold text-[#79747E] mb-0.5">Minutes</label>
                    <input
                      type="number"
                      min="0"
                      max="60"
                      value={fakeCallMinsInput}
                      onChange={(e) => {
                        const val = e.target.value;
                        setFakeCallMinsInput(val);
                        const m = parseInt(val || '0', 10) || 0;
                        const s = parseInt(fakeCallSecsInput || '0', 10) || 0;
                        setFakeCallDelaySec(Math.max(1, m * 60 + s));
                      }}
                      className="w-full h-[32px] bg-[#FEF7FF] border border-[#79747E]/30 rounded-lg px-2 text-xs font-mono font-bold text-[#1D1B20] focus:outline-none focus:border-[#6750A4]"
                    />
                  </div>
                  <div>
                    <label className="block text-[10px] font-bold text-[#79747E] mb-0.5">Seconds</label>
                    <input
                      type="number"
                      min="0"
                      max="59"
                      value={fakeCallSecsInput}
                      onChange={(e) => {
                        const val = e.target.value;
                        setFakeCallSecsInput(val);
                        const m = parseInt(fakeCallMinsInput || '0', 10) || 0;
                        const s = parseInt(val || '0', 10) || 0;
                        setFakeCallDelaySec(Math.max(1, m * 60 + s));
                      }}
                      className="w-full h-[32px] bg-[#FEF7FF] border border-[#79747E]/30 rounded-lg px-2 text-xs font-mono font-bold text-[#1D1B20] focus:outline-none focus:border-[#6750A4]"
                    />
                  </div>
                </div>
              </div>

              {/* 7. Simulator Options Switches */}
              <div className="space-y-1.5 bg-[#F3EDF7] p-2.5 rounded-xl border border-[#E7E0EC]">
                <label className="flex items-center justify-between cursor-pointer">
                  <div className="flex items-center gap-2">
                    <Volume2 className="w-3.5 h-3.5 text-[#6750A4]" />
                    <span className="text-xs font-bold text-[#1D1B20]">Play Ringtone Sound</span>
                  </div>
                  <input
                    type="checkbox"
                    checked={fakeCallSound}
                    onChange={(e) => setFakeCallSound(e.target.checked)}
                    className="w-4 h-4 accent-[#6750A4] rounded cursor-pointer"
                  />
                </label>

                <label className="flex items-center justify-between cursor-pointer">
                  <div className="flex items-center gap-2">
                    <Zap className="w-3.5 h-3.5 text-amber-600" />
                    <span className="text-xs font-bold text-[#1D1B20]">Simulate Vibration</span>
                  </div>
                  <input
                    type="checkbox"
                    checked={fakeCallVibrate}
                    onChange={(e) => setFakeCallVibrate(e.target.checked)}
                    className="w-4 h-4 accent-[#6750A4] rounded cursor-pointer"
                  />
                </label>

                <label className="flex items-center justify-between cursor-pointer">
                  <div className="flex items-center gap-2">
                    <History className="w-3.5 h-3.5 text-emerald-600" />
                    <span className="text-xs font-bold text-[#1D1B20]">Auto-Save to Call History on End</span>
                  </div>
                  <input
                    type="checkbox"
                    checked={fakeCallSaveToLog}
                    onChange={(e) => setFakeCallSaveToLog(e.target.checked)}
                    className="w-4 h-4 accent-[#6750A4] rounded cursor-pointer"
                  />
                </label>
              </div>

              {/* 8. Action Buttons */}
              <div className="flex gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => setIsFakeCallModalOpen(false)}
                  className="flex-1 py-2.5 text-xs font-bold text-[#49454F] bg-[#ECE6F0] hover:bg-[#E8DEF8] rounded-full transition"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setIsFakeCallModalOpen(false);
                    setFakeCallCountdown(fakeCallDelaySec);
                  }}
                  className="flex-[2] py-2.5 text-xs font-bold bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-600 hover:to-amber-700 text-white rounded-full shadow-md flex items-center justify-center gap-1.5 transition"
                >
                  <Zap className="w-4 h-4 fill-white" />
                  <span>Schedule Fake Call</span>
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Fake Incoming Call Fullscreen Screen (Pixel / Samsung / HyperOS / iOS Clones) */}
        {fakeCallState === 'incoming' && (
          <>
            {fakeCallBrand === 'samsung' ? (
              // Samsung One UI Authentic Incoming Screen
              <div className="absolute inset-0 bg-gradient-to-b from-[#132039] to-[#0C1424] text-white z-[60] flex flex-col justify-between p-7 select-none animate-in fade-in duration-200">
                <div className="flex flex-col items-center pt-8 text-center space-y-1">
                  <h2 className="text-[28px] font-bold text-white tracking-tight">{fakeCallName}</h2>
                  <p className="text-[13px] text-[#93C5FD] font-medium">Jio 5G (SIM {fakeCallSimSlot}) • {fakeCallNumber}</p>
                </div>

                <div className="flex items-center justify-center">
                  <div className="w-[100px] h-[100px] rounded-full bg-white/20 border-[1.5px] border-white/30 text-white text-4xl font-bold flex items-center justify-center shadow-2xl backdrop-blur-md">
                    {fakeCallName.slice(0, 1).toUpperCase()}
                  </div>
                </div>

                <div className="pb-6 px-5 space-y-4">
                  <div className="flex items-center justify-between">
                    <div className="flex flex-col items-center gap-1.5">
                      <button
                        onClick={() => {
                          setFakeCallState('active');
                          setFakeCallElapsedSec(0);
                        }}
                        className="w-16 h-16 rounded-full bg-[#22C55E] hover:bg-emerald-600 flex items-center justify-center text-white shadow-xl shadow-emerald-950/50 transform active:scale-90 transition animate-pulse"
                      >
                        <Phone className="w-7 h-7 fill-white text-white" />
                      </button>
                      <span className="text-xs font-semibold text-white">Answer</span>
                    </div>

                    <div className="flex flex-col items-center gap-1.5">
                      <button
                        onClick={() => setFakeCallState('idle')}
                        className="w-16 h-16 rounded-full bg-[#EF4444] hover:bg-red-600 flex items-center justify-center text-white shadow-xl shadow-red-950/50 transform active:scale-90 transition"
                      >
                        <Phone className="w-7 h-7 fill-white text-white rotate-[135deg]" />
                      </button>
                      <span className="text-xs font-semibold text-white">Decline</span>
                    </div>
                  </div>

                  <div className="flex justify-center">
                    <div className="px-5 py-1.5 rounded-full bg-white/10 text-white/90 text-xs font-medium flex items-center gap-1.5 backdrop-blur-md border border-white/5 shadow-xs">
                      <ChevronUp className="w-3.5 h-3.5 text-white/80" />
                      <span>Send message</span>
                    </div>
                  </div>
                </div>
              </div>
            ) : fakeCallBrand === 'hyperos' ? (
              // Xiaomi HyperOS Authentic Incoming Screen
              <div className="absolute inset-0 bg-[#17181A] text-white z-[60] flex flex-col justify-between p-7 select-none animate-in fade-in duration-200">
                <div className="flex flex-col items-center pt-8 text-center space-y-1">
                  <h2 className="text-[26px] font-bold text-white tracking-tight">{fakeCallName}</h2>
                  <p className="text-[13px] text-[#A1A1AA] font-medium">Mobile | India • SIM {fakeCallSimSlot}</p>
                  <p className="text-xs text-[#71717A] font-mono">{fakeCallNumber}</p>
                </div>

                <div className="flex items-center justify-center">
                  <div className="w-24 h-24 rounded-full bg-[#27272A] border-2 border-[#3F3F46] text-white text-4xl font-bold flex items-center justify-center shadow-xl">
                    {fakeCallName.slice(0, 1).toUpperCase()}
                  </div>
                </div>

                <div className="pb-6 px-5 space-y-4">
                  <div className="flex justify-center">
                    <div className="px-4 py-1.5 rounded-full bg-[#27272A] border border-[#3F3F46] text-[#A1A1AA] text-xs font-medium flex items-center gap-1.5 shadow-sm">
                      <MessageSquare className="w-3.5 h-3.5 text-[#A1A1AA]" />
                      <span>Quick response</span>
                    </div>
                  </div>

                  <div className="flex items-center justify-between">
                    <div className="flex flex-col items-center gap-1.5">
                      <button
                        onClick={() => setFakeCallState('idle')}
                        className="w-16 h-16 rounded-full bg-[#EF4444] hover:bg-red-600 flex items-center justify-center text-white shadow-xl transform active:scale-90 transition"
                      >
                        <Phone className="w-7 h-7 fill-white text-white rotate-[135deg]" />
                      </button>
                      <span className="text-[11px] font-medium text-[#A1A1AA]">Decline</span>
                    </div>

                    <div className="flex flex-col items-center gap-1.5">
                      <button
                        onClick={() => {
                          setFakeCallState('active');
                          setFakeCallElapsedSec(0);
                        }}
                        className="w-16 h-16 rounded-full bg-[#10B981] hover:bg-emerald-600 flex items-center justify-center text-white shadow-xl transform active:scale-90 transition animate-pulse"
                      >
                        <Phone className="w-7 h-7 fill-white text-white" />
                      </button>
                      <span className="text-[11px] font-medium text-[#A1A1AA]">Answer</span>
                    </div>
                  </div>
                </div>
              </div>
            ) : fakeCallBrand === 'ios' ? (
              // Apple iPhone iOS Authentic Incoming Screen
              <div className="absolute inset-0 bg-black text-white z-[60] flex flex-col justify-between p-7 select-none animate-in fade-in duration-200">
                <div className="flex flex-col items-center pt-10 text-center space-y-1">
                  <h2 className="text-[30px] font-light text-white tracking-tight">{fakeCallName}</h2>
                  <p className="text-sm text-[#8E8E93] font-normal">mobile • {fakeCallNumber}</p>
                </div>

                <div className="flex items-center justify-center">
                  <div className="w-24 h-24 rounded-full bg-[#1C1C1E] border border-white/10 text-white text-4xl font-normal flex items-center justify-center shadow-2xl">
                    {fakeCallName.slice(0, 1).toUpperCase()}
                  </div>
                </div>

                <div className="pb-8 px-6 space-y-6">
                  {/* iOS Signature Auxiliary Controls: Remind Me & Message */}
                  <div className="flex items-center justify-around px-2">
                    <div className="flex flex-col items-center gap-1.5">
                      <div className="w-12 h-12 rounded-full bg-white/15 backdrop-blur-md flex items-center justify-center text-white shadow-md border border-white/5">
                        <Clock className="w-5 h-5 text-white" />
                      </div>
                      <span className="text-[11px] font-normal text-white">remind me</span>
                    </div>

                    <div className="flex flex-col items-center gap-1.5">
                      <div className="w-12 h-12 rounded-full bg-white/15 backdrop-blur-md flex items-center justify-center text-white shadow-md border border-white/5">
                        <MessageSquare className="w-5 h-5 text-white" />
                      </div>
                      <span className="text-[11px] font-normal text-white">message</span>
                    </div>
                  </div>

                  {/* iOS Primary Action Controls: Decline (red) & Accept (green) */}
                  <div className="flex items-center justify-between px-2">
                    <div className="flex flex-col items-center gap-2">
                      <button
                        onClick={() => setFakeCallState('idle')}
                        className="w-[68px] h-[68px] rounded-full bg-[#FF3B30] hover:bg-red-600 flex items-center justify-center text-white shadow-xl shadow-red-950/60 transform active:scale-90 transition"
                      >
                        <Phone className="w-8 h-8 fill-white text-white rotate-[135deg]" />
                      </button>
                      <span className="text-xs font-normal text-white">decline</span>
                    </div>

                    <div className="flex flex-col items-center gap-2">
                      <button
                        onClick={() => {
                          setFakeCallState('active');
                          setFakeCallElapsedSec(0);
                        }}
                        className="w-[68px] h-[68px] rounded-full bg-[#34C759] hover:bg-emerald-500 flex items-center justify-center text-white shadow-xl shadow-emerald-950/60 transform active:scale-90 transition animate-pulse"
                      >
                        <Phone className="w-8 h-8 fill-white text-white" />
                      </button>
                      <span className="text-xs font-normal text-white">accept</span>
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              // Google Pixel Material You Standard Incoming Screen
              <div className="absolute inset-0 bg-[#121214] text-white z-[60] flex flex-col justify-between p-7 select-none animate-in fade-in duration-200">
                <div className="flex flex-col items-center pt-6 space-y-2">
                  <div className="px-3.5 py-1 rounded-full bg-[#2B2930] text-[11px] font-bold text-[#D0BCFF] flex items-center gap-1.5 border border-white/5">
                    <Smartphone className="w-3.5 h-3.5 text-[#D0BCFF]" />
                    <span>Incoming call • SIM {fakeCallSimSlot}</span>
                  </div>

                  <div className="text-center pt-2">
                    <h2 className="text-2xl font-bold tracking-tight text-white">{fakeCallName}</h2>
                    <p className="text-xs text-[#CAC4D0] font-mono mt-1">{fakeCallNumber}</p>
                  </div>
                </div>

                <div className="flex items-center justify-center py-4">
                  <div className="relative flex items-center justify-center">
                    <div className="absolute w-44 h-44 rounded-full bg-white/5 animate-ping duration-1000 pointer-events-none" />
                    <div className="absolute w-36 h-36 rounded-full bg-white/10 animate-pulse pointer-events-none" />
                    <div className="relative w-28 h-28 min-w-[112px] min-h-[112px] aspect-square rounded-full bg-gradient-to-tr from-[#6750A4] to-[#4F378B] flex items-center justify-center text-4xl font-bold text-white shadow-2xl border-2 border-white/20 shrink-0">
                      {fakeCallName.slice(0, 1).toUpperCase()}
                    </div>
                  </div>
                </div>

                <div className="pb-6 px-4 space-y-4">
                  <div className="flex justify-center">
                    <div className="px-4 py-1.5 rounded-full bg-[#2B2930] text-[#D0BCFF] text-xs font-semibold flex items-center gap-1.5 border border-white/5 shadow-sm">
                      <MessageSquare className="w-3.5 h-3.5 text-[#D0BCFF]" />
                      <span>Reply</span>
                    </div>
                  </div>

                  <div className="flex items-center justify-around">
                    <div className="flex flex-col items-center gap-1.5">
                      <button
                        onClick={() => setFakeCallState('idle')}
                        className="w-16 h-16 rounded-full bg-[#BA1A1A] hover:bg-[#DC2626] flex items-center justify-center text-white shadow-xl shadow-red-950/50 transform active:scale-90 transition"
                      >
                        <Phone className="w-7 h-7 fill-white text-white rotate-[135deg]" />
                      </button>
                      <span className="text-xs font-medium text-[#CAC4D0]">Decline</span>
                    </div>

                    <div className="flex flex-col items-center gap-1.5">
                      <button
                        onClick={() => {
                          setFakeCallState('active');
                          setFakeCallElapsedSec(0);
                        }}
                        className="w-16 h-16 rounded-full bg-[#1E8E3E] hover:bg-emerald-600 flex items-center justify-center text-white shadow-xl shadow-emerald-950/50 transform active:scale-90 transition animate-bounce"
                      >
                        <Phone className="w-7 h-7 fill-white text-white" />
                      </button>
                      <span className="text-xs font-medium text-[#CAC4D0]">Answer</span>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </>
        )}

        {/* Authentic In-Call Screen (Pixel / Samsung / HyperOS / iOS Clones) */}
        {fakeCallState === 'active' && (
          <div
            className={`absolute inset-0 text-white z-[60] flex flex-col justify-between p-6 animate-in fade-in duration-150 select-none ${
              fakeCallBrand === 'samsung'
                ? 'bg-gradient-to-b from-[#132039] to-[#0C1424]'
                : fakeCallBrand === 'hyperos'
                ? 'bg-[#17181A]'
                : fakeCallBrand === 'ios'
                ? 'bg-black'
                : 'bg-[#121214]'
            }`}
          >
            {/* Top Header - Authentic to Brand */}
            {fakeCallBrand === 'samsung' ? (
              <div className="flex flex-col items-center pt-6 space-y-1">
                <h2 className="text-[26px] font-bold text-white tracking-tight">{fakeCallName}</h2>
                <p className="text-[13px] text-[#93C5FD]">Jio 5G (SIM {fakeCallSimSlot}) • {fakeCallNumber}</p>
                <div className="text-base font-semibold pt-1">
                  {isHold ? <span className="text-amber-400 font-bold">Call on hold</span> : <span className="text-[#38BDF8]">{formatCallTimer(fakeCallElapsedSec)}</span>}
                </div>
              </div>
            ) : fakeCallBrand === 'hyperos' ? (
              <div className="flex flex-col items-center pt-6 space-y-1">
                <h2 className="text-2xl font-bold text-white tracking-tight">{fakeCallName}</h2>
                <p className="text-xs text-[#A1A1AA]">Mobile | India • SIM {fakeCallSimSlot}</p>
                <div className="text-sm font-bold pt-1">
                  {isHold ? <span className="text-amber-400 font-bold">Call on hold</span> : <span className="text-[#22C55E]">{formatCallTimer(fakeCallElapsedSec)}</span>}
                </div>
              </div>
            ) : fakeCallBrand === 'ios' ? (
              <div className="flex flex-col items-center pt-8 space-y-1">
                <h2 className="text-[28px] font-light text-white tracking-tight">{fakeCallName}</h2>
                <div className="text-sm text-[#8E8E93] font-normal pt-0.5">
                  {isHold ? <span className="text-amber-400 font-semibold">call on hold</span> : formatCallTimer(fakeCallElapsedSec)}
                </div>
              </div>
            ) : (
              // Google Pixel Header
              <div className="flex flex-col items-center pt-3 space-y-1">
                <div className="px-2.5 py-0.5 rounded-xl bg-[#2B2930] text-[11px] font-bold text-[#D0BCFF] flex items-center gap-1.5 mb-1.5">
                  <Smartphone className="w-3 h-3 text-[#D0BCFF]" />
                  <span>SIM {fakeCallSimSlot} • HD</span>
                </div>
                <div className="w-[72px] h-[72px] rounded-full bg-gradient-to-tr from-[#6750A4] to-[#4F378B] flex items-center justify-center text-3xl font-black text-white shadow-lg border border-white/10 mb-1">
                  {fakeCallName.slice(0, 1).toUpperCase()}
                </div>
                <h2 className="text-[22px] font-bold tracking-tight text-white">{fakeCallName}</h2>
                <p className="text-[13px] text-[#CAC4D0] font-mono">{fakeCallNumber}</p>
                <div className="text-sm font-medium text-[#C4C7C5] pt-0.5">
                  {isHold ? <span className="text-amber-400 font-bold">Call on hold</span> : formatCallTimer(fakeCallElapsedSec)}
                </div>
              </div>
            )}

            {/* Center Area: DTMF Keypad or Authentic Brand 6-Button Grid */}
            {isKeypadOpen ? (
              <div className="bg-[#1E1F22] rounded-[32px] p-4 border border-white/10 my-auto shadow-2xl">
                <div className="text-center pb-2">
                  <div className="text-xl font-bold font-mono tracking-widest text-white h-7">
                    {dialedDigits || <span className="text-white/30 text-sm">Keypad</span>}
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-2.5 max-w-[260px] mx-auto">
                  {[
                    { d: '1', sub: '' },
                    { d: '2', sub: 'ABC' },
                    { d: '3', sub: 'DEF' },
                    { d: '4', sub: 'GHI' },
                    { d: '5', sub: 'JKL' },
                    { d: '6', sub: 'MNO' },
                    { d: '7', sub: 'PQRS' },
                    { d: '8', sub: 'TUV' },
                    { d: '9', sub: 'WXYZ' },
                    { d: '*', sub: '' },
                    { d: '0', sub: '+' },
                    { d: '#', sub: '' },
                  ].map((k) => (
                    <button
                      key={k.d}
                      onClick={() => {
                        if (DTMF_FREQS[k.d]) {
                          playTone(DTMF_FREQS[k.d][0], DTMF_FREQS[k.d][1], 0.15);
                        }
                        setDialedDigits((prev) => prev + k.d);
                      }}
                      className="h-13 rounded-2xl bg-[#2B2930] hover:bg-[#3B383E] active:bg-[#6750A4] flex flex-col items-center justify-center transition"
                    >
                      <span className="text-lg font-black leading-none">{k.d}</span>
                      {k.sub && <span className="text-[8px] font-bold text-white/50">{k.sub}</span>}
                    </button>
                  ))}
                </div>
                <button
                  onClick={() => setIsKeypadOpen(false)}
                  className="mt-3 w-full py-2 text-xs font-bold text-[#D0BCFF] hover:text-white bg-white/5 rounded-xl transition"
                >
                  Hide Keypad
                </button>
              </div>
            ) : (
              <div className="grid grid-cols-3 gap-y-5 gap-x-4 px-3 my-auto">
                {/* 1. Mute */}
                <div className="flex flex-col items-center gap-1.5">
                  <button
                    onClick={() => setIsMuted(!isMuted)}
                    className={`w-14 h-14 ${
                      fakeCallBrand === 'pixel' ? 'rounded-[18px]' : 'rounded-full'
                    } flex items-center justify-center transition shadow-sm ${
                      isMuted
                        ? 'bg-white text-black'
                        : fakeCallBrand === 'ios'
                        ? 'bg-white/15 text-white hover:bg-white/25'
                        : fakeCallBrand === 'samsung'
                        ? 'bg-white/20 text-white hover:bg-white/30 backdrop-blur-md'
                        : 'bg-[#2B2930] text-[#E6E1E5] hover:bg-[#36343B]'
                    }`}
                  >
                    {isMuted ? <MicOff className="w-6 h-6" /> : <Mic className="w-6 h-6" />}
                  </button>
                  <span className="text-[11px] font-medium text-[#CAC4D0]">
                    {fakeCallBrand === 'ios' ? 'mute' : isMuted ? 'Unmute' : 'Mute'}
                  </span>
                </div>

                {/* 2. Keypad */}
                <div className="flex flex-col items-center gap-1.5">
                  <button
                    onClick={() => setIsKeypadOpen(true)}
                    className={`w-14 h-14 ${
                      fakeCallBrand === 'pixel' ? 'rounded-[18px]' : 'rounded-full'
                    } flex items-center justify-center transition shadow-sm ${
                      fakeCallBrand === 'ios'
                        ? 'bg-white/15 text-white hover:bg-white/25'
                        : fakeCallBrand === 'samsung'
                        ? 'bg-white/20 text-white hover:bg-white/30 backdrop-blur-md'
                        : 'bg-[#2B2930] text-[#E6E1E5] hover:bg-[#36343B]'
                    }`}
                  >
                    <div className="w-5 h-5 grid grid-cols-3 gap-0.5 items-center justify-center">
                      {Array.from({ length: 9 }).map((_, i) => (
                        <span key={i} className="w-1 h-1 bg-[#E6E1E5] rounded-full mx-auto" />
                      ))}
                    </div>
                  </button>
                  <span className="text-[11px] font-medium text-[#CAC4D0]">
                    {fakeCallBrand === 'ios' ? 'keypad' : 'Keypad'}
                  </span>
                </div>

                {/* 3. Speaker */}
                <div className="flex flex-col items-center gap-1.5">
                  <button
                    onClick={() => setIsSpeakerOn(!isSpeakerOn)}
                    className={`w-14 h-14 ${
                      fakeCallBrand === 'pixel' ? 'rounded-[18px]' : 'rounded-full'
                    } flex items-center justify-center transition shadow-sm ${
                      isSpeakerOn
                        ? 'bg-white text-black'
                        : fakeCallBrand === 'ios'
                        ? 'bg-white/15 text-white hover:bg-white/25'
                        : fakeCallBrand === 'samsung'
                        ? 'bg-white/20 text-white hover:bg-white/30 backdrop-blur-md'
                        : 'bg-[#2B2930] text-[#E6E1E5] hover:bg-[#36343B]'
                    }`}
                  >
                    {isSpeakerOn ? <Volume2 className="w-6 h-6" /> : <VolumeX className="w-6 h-6" />}
                  </button>
                  <span className="text-[11px] font-medium text-[#CAC4D0]">
                    {fakeCallBrand === 'ios' ? 'audio' : 'Speaker'}
                  </span>
                </div>

                {/* 4. Add call */}
                <div className="flex flex-col items-center gap-1.5">
                  <button
                    onClick={() => {}}
                    className={`w-14 h-14 ${
                      fakeCallBrand === 'pixel' ? 'rounded-[18px]' : 'rounded-full'
                    } flex items-center justify-center transition shadow-sm ${
                      fakeCallBrand === 'ios'
                        ? 'bg-white/15 text-white hover:bg-white/25'
                        : fakeCallBrand === 'samsung'
                        ? 'bg-white/20 text-white hover:bg-white/30 backdrop-blur-md'
                        : 'bg-[#2B2930] text-[#E6E1E5] hover:bg-[#36343B]'
                    }`}
                  >
                    <Plus className="w-6 h-6" />
                  </button>
                  <span className="text-[11px] font-medium text-[#CAC4D0]">
                    {fakeCallBrand === 'ios' ? 'add call' : 'Add call'}
                  </span>
                </div>

                {/* 5. Hold / FaceTime */}
                <div className="flex flex-col items-center gap-1.5">
                  <button
                    onClick={() => setIsHold(!isHold)}
                    className={`w-14 h-14 ${
                      fakeCallBrand === 'pixel' ? 'rounded-[18px]' : 'rounded-full'
                    } flex items-center justify-center transition shadow-sm ${
                      isHold
                        ? 'bg-amber-400 text-black'
                        : fakeCallBrand === 'ios'
                        ? 'bg-white/15 text-white hover:bg-white/25'
                        : fakeCallBrand === 'samsung'
                        ? 'bg-white/20 text-white hover:bg-white/30 backdrop-blur-md'
                        : 'bg-[#2B2930] text-[#E6E1E5] hover:bg-[#36343B]'
                    }`}
                  >
                    {fakeCallBrand === 'ios' ? <Video className="w-6 h-6" /> : <Pause className="w-6 h-6" />}
                  </button>
                  <span className="text-[11px] font-medium text-[#CAC4D0]">
                    {fakeCallBrand === 'ios' ? 'FaceTime' : isHold ? 'Unhold' : 'Hold'}
                  </span>
                </div>

                {/* 6. Record / Contacts */}
                <div className="flex flex-col items-center gap-1.5">
                  <button
                    onClick={() => setIsRecording(!isRecording)}
                    className={`w-14 h-14 ${
                      fakeCallBrand === 'pixel' ? 'rounded-[18px]' : 'rounded-full'
                    } flex items-center justify-center transition shadow-sm ${
                      isRecording
                        ? 'bg-rose-500 text-white'
                        : fakeCallBrand === 'ios'
                        ? 'bg-white/15 text-white hover:bg-white/25'
                        : fakeCallBrand === 'samsung'
                        ? 'bg-white/20 text-white hover:bg-white/30 backdrop-blur-md'
                        : 'bg-[#2B2930] text-[#E6E1E5] hover:bg-[#36343B]'
                    }`}
                  >
                    {fakeCallBrand === 'ios' ? (
                      <Users className="w-6 h-6" />
                    ) : (
                      <Disc className={`w-6 h-6 ${isRecording ? 'animate-spin' : ''}`} />
                    )}
                  </button>
                  <span className="text-[11px] font-medium text-[#CAC4D0]">
                    {fakeCallBrand === 'ios' ? 'contacts' : isRecording ? 'Recording' : 'Record'}
                  </span>
                </div>
              </div>
            )}

            {/* Bottom End Call (Brand Authentic) */}
            <div className="pb-8 flex justify-center">
              <button
                onClick={() => {
                  if (fakeCallSaveToLog) {
                    const newLog: CallLogItem = {
                      id: Date.now().toString(),
                      name: fakeCallName,
                      number: fakeCallNumber,
                      type: 1, // Incoming
                      date: new Date(),
                      durationSeconds: Math.max(1, fakeCallElapsedSec),
                      simSlot: fakeCallSimSlot,
                    };
                    setLogs((prev) => [newLog, ...prev]);
                  }
                  setFakeCallState('idle');
                  setIsKeypadOpen(false);
                  setDialedDigits('');
                  setIsHold(false);
                  setIsRecording(false);
                }}
                className={`flex items-center justify-center text-white shadow-xl shadow-red-950/50 transform active:scale-95 transition-all ${
                  fakeCallBrand === 'samsung'
                    ? 'w-[76px] h-[54px] rounded-[27px] bg-[#E11D48] hover:bg-rose-700'
                    : fakeCallBrand === 'ios'
                    ? 'w-[68px] h-[68px] rounded-full bg-[#FF3B30] hover:bg-red-600'
                    : 'w-[68px] h-[68px] rounded-full bg-[#BA1A1A] hover:bg-[#DC2626]'
                }`}
                title="End Call"
              >
                <Phone className="w-8 h-8 fill-white text-white rotate-[135deg]" />
              </button>
            </div>
          </div>
        )}

        {/* Animated Splash Screen Overlay (Zero slide, clean fade-out reveal) */}
        {splashState !== 'hidden' && (
          <div
            className={`absolute inset-0 bg-[#FEF7FF] z-50 flex flex-col items-center justify-between py-10 transition-opacity duration-300 ease-out pointer-events-none ${
              splashState === 'fading' ? 'opacity-0' : 'opacity-100'
            }`}
          >
            <div className="flex-1 flex flex-col items-center justify-center">
              <div className="animate-in zoom-in-75 fade-in duration-700 ease-out flex flex-col items-center gap-4">
                <div className="w-[88px] h-[88px] rounded-[24px] bg-white border border-[#E7E0EC] shadow-xl flex items-center justify-center overflow-hidden p-2">
                  <img src="/logo.png" alt="CallCraft" className="w-full h-full object-contain rounded-[18px]" />
                </div>
                <div className="text-center">
                  <div className="flex items-center justify-center text-3xl font-black tracking-tight">
                    <span className="text-[#1D1B20]">Call</span>
                    <span className="text-[#6750A4]">Craft</span>
                  </div>
                  <p className="text-xs font-semibold text-[#79747E] tracking-widest uppercase mt-1">
                    Modern Call Log Editor
                  </p>
                </div>
              </div>
            </div>
            <div className="text-[11px] font-bold text-[#79747E]/60 tracking-wider">
              v1.0.0
            </div>
          </div>
        )}

        {/* Bottom Pill Toast Host (with custom shrink & slide-down exit animation) */}
        {toastState && (
          <div className="absolute inset-x-0 bottom-8 z-50 flex justify-center pointer-events-none px-4">
            <div
              className={`flex items-center gap-2 bg-[#1D1B20] text-white px-3.5 py-1.5 rounded-full shadow-2xl border border-white/10 transition-all duration-200 ease-[cubic-bezier(0.2,0,0,1)] ${
                toastState.isExiting
                  ? 'opacity-0 translate-y-4 scale-75'
                  : 'opacity-100 translate-y-0 scale-100 animate-in fade-in slide-in-from-bottom-2 duration-150'
              }`}
            >
              <Check className="w-3.5 h-3.5 text-[#10B981] stroke-[2.5]" />
              <span className="text-xs font-semibold whitespace-nowrap">{toastState.message}</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
