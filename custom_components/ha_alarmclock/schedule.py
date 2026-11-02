"""Wall-clock scheduling shared by commands; no HA runtime required."""
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo
DAYS = ['mon','tue','wed','thu','fri','sat','sun']

def next_trigger(hour, minute, zone, after, days=()):
    if hour not in range(24) or minute not in range(60) or any(day not in DAYS for day in days):
        raise ValueError('Invalid alarm schedule')
    tz=ZoneInfo(zone)
    today=datetime.fromtimestamp(after,tz).date()
    for offset in range(8):
        date=today+timedelta(days=offset)
        if days and DAYS[date.weekday()] not in days:continue
        local=datetime(date.year,date.month,date.day,hour,minute)
        candidates=[]
        for fold in (0,1):
            candidate=local.replace(tzinfo=tz,fold=fold).timestamp()
            if datetime.fromtimestamp(candidate,tz).replace(tzinfo=None)==local:
                candidates.append(candidate)
        # Nonexistent spring time: move forward by the gap, matching Android java.time.
        if not candidates:candidates=[local.replace(tzinfo=tz,fold=0).timestamp()]
        candidates=[stamp for stamp in candidates if stamp>after]
        if candidates:return min(candidates)
    raise ValueError('No next trigger')
