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
  ShieldAlert,
  Check,
  Clock,
  CheckCheck,
  Edit3
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
  const [logs, setLogs] = useState<CallLogItem[]>(INITIAL_LOGS);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeCategoryFilter, setActiveCategoryFilter] = useState<number | 'ALL'>('ALL');
  const [isDialerRoleGranted, setIsDialerRoleGranted] = useState(true);

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
      const key = cleanNumber || item.name.trim().toLowerCase();
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
              transform: currentScreen === 'recents' ? 'translateX(0%)' : 'translateX(-100%)',
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

                    <button
                      onClick={() => setCurrentScreen('settings')}
                      className="p-2 -mr-2 text-[#49454F] hover:text-[#1D1B20] hover:bg-black/5 rounded-full transition"
                    >
                      <MoreVertical className="w-5 h-5" />
                    </button>
                  </div>
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
            <div className="w-full h-full shrink-0 flex flex-col overflow-y-auto px-5 py-4 space-y-4">
              <div className="flex items-center gap-3 pb-2 border-b border-[#E7E0EC] shrink-0">
                <button
                  onClick={() => setCurrentScreen('recents')}
                  className="p-2 -ml-2 rounded-full hover:bg-black/5 text-[#1D1B20] transition"
                >
                  <ArrowLeft className="w-5 h-5" />
                </button>
                <div>
                  <h2 className="text-lg font-black text-[#1D1B20]">Settings</h2>
                  <p className="text-[11px] text-[#6750A4] font-semibold">Preferences & Permissions</p>
                </div>
              </div>

              <div>
                <span className="text-[11px] font-bold text-[#6750A4] uppercase tracking-wider px-2 block mb-2">
                  Permissions & System
                </span>

                <div className="bg-[#FEF7FF] rounded-[24px] p-4 border border-[#E7E0EC] shadow-xs space-y-3">
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-3 min-w-0">
                      <div className={`w-10 h-10 rounded-2xl flex items-center justify-center shrink-0 ${
                        isDialerRoleGranted ? 'bg-[#10B981]/15 text-[#10B981]' : 'bg-[#6750A4]/15 text-[#6750A4]'
                      }`}>
                        {isDialerRoleGranted ? <ShieldCheck className="w-5 h-5" /> : <ShieldAlert className="w-5 h-5" />}
                      </div>
                      <div className="min-w-0">
                        <h3 className="text-sm font-bold text-[#1D1B20] leading-tight">Dialer Role</h3>
                        <p className="text-xs text-[#49454F] font-medium leading-tight mt-0.5">Default Phone App permission</p>
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={() => setIsDialerRoleGranted(!isDialerRoleGranted)}
                      style={{ width: '52px', height: '32px' }}
                      className={`relative inline-flex shrink-0 items-center rounded-full p-1 transition-colors duration-300 ${
                        isDialerRoleGranted ? 'bg-[#6750A4]' : 'bg-[#ECE6F0] border border-[#E7E0EC]'
                      }`}
                    >
                      <span
                        className={`pointer-events-none inline-flex h-6 w-6 transform items-center justify-center rounded-full bg-white shadow-md transition duration-300 ${
                          isDialerRoleGranted ? 'translate-x-5' : 'translate-x-0'
                        }`}
                      >
                        {isDialerRoleGranted && <Check className="w-3.5 h-3.5 text-[#6750A4] stroke-[3]" />}
                      </span>
                    </button>
                  </div>

                  <div className={`rounded-xl px-3 py-2 flex items-center gap-2 ${
                    isDialerRoleGranted ? 'bg-[#10B981]/10 text-[#10B981]' : 'bg-[#F59E0B]/10 text-[#F59E0B]'
                  }`}>
                    <span className="w-2 h-2 rounded-full bg-current shrink-0" />
                    <span className="text-xs font-bold leading-tight">
                      {isDialerRoleGranted ? 'Role Active • Ready to edit logs' : 'Role Inactive • Turn switch ON to edit'}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Google Phone Standard: Contact Call History Bottom Sheet */}
        {viewingGroup && (
          <div className="absolute inset-0 bg-black/50 backdrop-blur-xs flex items-end justify-center z-50 animate-in fade-in duration-150">
            <div className="bg-[#FEF7FF] text-[#1D1B20] w-full rounded-t-[36px] p-6 shadow-2xl flex flex-col space-y-4 max-h-[88%] overflow-y-auto border-t border-[#E7E0EC]">
              <div className="w-12 h-1 bg-[#79747E]/40 rounded-full mx-auto shrink-0 mb-1" />

              {/* Contact Monogram & Details */}
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-12 h-12 rounded-full bg-gradient-to-tr from-[#6750A4] to-[#4F378B] text-white flex items-center justify-center text-lg font-black shadow-sm">
                    {viewingGroup.name.slice(0, 1).toUpperCase()}
                  </div>
                  <div>
                    <h3 className="text-lg font-black text-[#1D1B20] leading-tight">{viewingGroup.name}</h3>
                    <p className="text-xs text-[#79747E] font-medium font-mono">{viewingGroup.number}</p>
                  </div>
                </div>

                <button
                  onClick={() => setViewingGroup(null)}
                  className="w-8 h-8 rounded-full bg-[#ECE6F0] flex items-center justify-center text-[#49454F] hover:bg-[#E8DEF8]"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* Quick Actions: + Log Call & Delete All */}
              <div className="grid grid-cols-2 gap-2 pt-1">
                <button
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
                  onClick={() => handleDeleteAllForGroup(viewingGroup)}
                  className="py-2.5 rounded-xl bg-rose-50 text-[#F43F5E] border border-rose-200 text-xs font-bold flex items-center justify-center gap-1.5 hover:bg-rose-100 transition"
                >
                  <Trash2 className="w-4 h-4" />
                  <span>Delete All</span>
                </button>
              </div>

              {/* Call History Timeline Header */}
              <div className="pt-2">
                <span className="text-[10px] font-bold text-[#6750A4] uppercase tracking-wider block mb-2">
                  Call History ({viewingGroup.calls.length})
                </span>

                <div className="space-y-2">
                  {viewingGroup.calls.map((call) => {
                    const typeMeta = CALL_TYPES.find((c) => c.type === call.type) || CALL_TYPES[0];
                    const Icon = typeMeta.icon;

                    return (
                      <div
                        key={call.id}
                        className="bg-[#F3EDF7] rounded-2xl p-3 flex items-center justify-between gap-3 hover:bg-[#ECE6F0] transition"
                      >
                        <div className="flex items-center gap-2.5 min-w-0 flex-1">
                          <div className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 ${typeMeta.bgLight}`}>
                            <Icon className={`w-4 h-4 ${typeMeta.iconColor} stroke-[2.2]`} />
                          </div>

                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-1.5">
                              <span className="text-xs font-bold text-[#1D1B20]">{typeMeta.label}</span>
                              <span className={`text-[8px] font-black px-1.5 py-0.5 rounded-md ${
                                call.simSlot === 1 ? 'bg-[#E8DEF8] text-[#6750A4]' : 'bg-[#E0F2FE] text-[#0284C7]'
                              }`}>
                                SIM {call.simSlot}
                              </span>
                            </div>
                            <p className="text-[11px] text-[#49454F] font-medium">
                              {formatFullDateTime(call.date)} {call.durationSeconds > 0 && `• ${formatDuration(call.durationSeconds)}`}
                            </p>
                          </div>
                        </div>

                        <div className="flex items-center gap-1 shrink-0">
                          <button
                            onClick={() => {
                              setViewingGroup(null);
                              openEditModal(call);
                            }}
                            className="p-1.5 rounded-full hover:bg-purple-100 text-[#6750A4]"
                            title="Edit"
                          >
                            <Edit3 className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => {
                              setLogs((prev) => prev.filter((item) => item.id !== call.id));
                            }}
                            className="p-1.5 rounded-full hover:bg-rose-100 text-[#F43F5E]"
                            title="Delete"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
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
      </div>
    </div>
  );
}
