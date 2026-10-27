import { useState, useCallback, useRef } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin from "@fullcalendar/interaction";
import { getEquipmentAvailability } from "../../services/bookingService";

export default function AvailabilityCalendar({
  equipmentId,
  onSelectSlot,
  reloadTrigger = 0,
}) {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const calendarRef = useRef(null);

  const fetchSlots = useCallback(
    async (info) => {
      if (!equipmentId) {
        setEvents([]);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const slots = await getEquipmentAvailability(
          equipmentId,
          info.startStr,
          info.endStr
        );

        const mappedEvents = (Array.isArray(slots) ? slots : []).map((slot, index) => {
          let title = "Reserved";
          let className = "fc-event-confirmed";

          if (slot.status === "PENDING_APPROVAL") {
            title = "Pending Approval";
            className = "fc-event-pending";
          } else if (slot.status === "IN_USE") {
            title = "Currently In Use";
            className = "fc-event-in-use";
          }

          return {
            id: `slot-${index}-${slot.startTime}`,
            title,
            start: slot.startTime,
            end: slot.endTime,
            className,
            extendedProps: {
              status: slot.status,
            },
          };
        });

        setEvents(mappedEvents);
      } catch (err) {
        console.error("Failed to load availability slots:", err);
        setError("Unable to load availability data for the selected range.");
      } finally {
        setLoading(false);
      }
    },
    [equipmentId]
  );

  return (
    <div className="calendar-card">
      <div className="calendar-legend">
        <span className="legend-item">
          <span className="legend-color available" />
          <span>Available Time (Click to Reserve)</span>
        </span>
        <span className="legend-item">
          <span className="legend-color confirmed" />
          <span>Confirmed Reservation</span>
        </span>
        <span className="legend-item">
          <span className="legend-color pending" />
          <span>Pending Approval</span>
        </span>
        <span className="legend-item">
          <span className="legend-color in-use" />
          <span>In Use</span>
        </span>
        {loading && (
          <span style={{ marginLeft: "auto", fontSize: "12px", color: "var(--primary-dark)" }}>
            ⏳ Loading availability...
          </span>
        )}
      </div>

      {error && (
        <div className="alert-error" style={{ marginBottom: "16px" }}>
          <span>{error}</span>
        </div>
      )}

      <FullCalendar
        ref={calendarRef}
        key={`${equipmentId}-${reloadTrigger}`}
        plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
        initialView="timeGridWeek"
        headerToolbar={{
          left: "prev,next today",
          center: "title",
          right: "dayGridMonth,timeGridWeek,timeGridDay",
        }}
        slotMinTime="07:00:00"
        slotMaxTime="22:00:00"
        allDaySlot={false}
        slotDuration="00:30:00"
        selectable={true}
        selectMirror={true}
        datesSet={fetchSlots}
        events={events}
        select={(selectionInfo) => {
          if (onSelectSlot) {
            onSelectSlot({
              start: selectionInfo.start,
              end: selectionInfo.end,
              startStr: selectionInfo.startStr,
              endStr: selectionInfo.endStr,
            });
          }
        }}
        eventClick={(clickInfo) => {
          const props = clickInfo.event.extendedProps;
          alert(
            `Selected interval is unavailable (${clickInfo.event.title}).\n` +
            `From: ${new Date(clickInfo.event.start).toLocaleString()}\n` +
            `To: ${new Date(clickInfo.event.end).toLocaleString()}\n` +
            `Status: ${props.status || "Booked"}`
          );
        }}
        height="auto"
        nowIndicator={true}
      />
    </div>
  );
}
