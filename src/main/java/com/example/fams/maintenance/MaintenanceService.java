package com.example.fams.maintenance;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetService;
import com.example.fams.aau.keycloak.SyncedUser;
import com.example.fams.aau.keycloak.SyncedUserRepository;
import com.example.fams.mail.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    @Value("${fams.base-url:http://localhost:9090}")
    private String appBaseUrl;

    /**
     * Upper bound on how many missed intervals a single schedule will generate in one pass.
     * Guarantees the backlog catch-up loop always terminates even for schedules whose
     * nextDueDate is far in the past. 24 covers roughly WEEKLY maintenance for ~6 months.
     */
    private static final int MAX_BACKLOG = 24;

    /**
     * Reject start dates older than this to avoid spawning an enormous backlog of tasks.
     */
    private static final LocalDate MIN_START_DATE = LocalDate.now().minusYears(5);

    private final AssetService assetService;
    private final MaintenanceScheduleRepository scheduleRepository;
    private final MaintenanceRecordRepository recordRepository;
    private final MaintenanceTaskRepository taskRepository;
    private final RabbitTemplate rabbitTemplate;
    private final SyncedUserRepository syncedUserRepository;
    private final EmailService emailService;

    public MaintenanceService(AssetService assetService,
                              MaintenanceScheduleRepository scheduleRepository,
                              MaintenanceRecordRepository recordRepository,
                              MaintenanceTaskRepository taskRepository,
                              RabbitTemplate rabbitTemplate,
                              SyncedUserRepository syncedUserRepository,
                              EmailService emailService) {
        this.assetService = assetService;
        this.scheduleRepository = scheduleRepository;
        this.recordRepository = recordRepository;
        this.taskRepository = taskRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.syncedUserRepository = syncedUserRepository;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceSchedule> schedules() {
        return scheduleRepository.findAllByOrderByNextDueDateAsc();
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRecord> historyForAsset(Long assetId) {
        Asset asset = assetService.findById(assetId);
        return recordRepository.findByAssetOrderByMaintenanceDateDescCreatedAtDesc(asset);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceTask> recentTasks() {
        return taskRepository.findTop8ByStatusOrderByDueDateDescCreatedAtDesc(MaintenanceStatus.DUE);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceTask> recentResolvedTasks() {
        return taskRepository.findTop8ByStatusOrderByDueDateDescCreatedAtDesc(MaintenanceStatus.COMPLETED);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRecord> recentCorrectiveRecords() {
        return recordRepository.findTop8ByTypeOrderByMaintenanceDateDescCreatedAtDesc(MaintenanceType.CORRECTIVE);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRecord> recentRequests() {
        return recordRepository.findTop8ByStatusOrderByMaintenanceDateDescCreatedAtDesc(MaintenanceStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceReportRow> report(LocalDate start, LocalDate end) {
        return recordRepository.findByMaintenanceDateBetweenOrderByMaintenanceDateDesc(start, end)
                .stream()
                .map(record -> new MaintenanceReportRow(
                        record.getAsset().getId(),
                        record.getAsset().getAssetCode(),
                        record.getAsset().getName(),
                        record.getAsset().getCategory(),
                        record.getType(),
                        record.getServiceProvider(),
                        record.getMaintenanceDate(),
                        record.getResolutionDate(),
                        record.getMaintenanceCost(),
                        record.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal reportTotal(LocalDate start, LocalDate end) {
        return recordRepository.findByMaintenanceDateBetweenOrderByMaintenanceDateDesc(start, end)
                .stream()
                .map(MaintenanceRecord::getMaintenanceCost)
                .filter(cost -> cost != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public long correctiveCount() {
        return recordRepository.countByType(MaintenanceType.CORRECTIVE);
    }

    @Transactional(readOnly = true)
    public long dueTaskCount() {
        return taskRepository.countByStatus(MaintenanceStatus.DUE);
    }

    @Transactional
    public MaintenanceSchedule createSchedule(Long assetId,
                                              String assetCategory,
                                              String serviceType,
                                              MaintenanceFrequency frequency,
                                              LocalDate startDate,
                                              String responsibleParty,
                                              String responsibleRole) {
        if (frequency == null) {
            throw new IllegalArgumentException("Maintenance frequency is required.");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("Maintenance start date is required.");
        }
        if (startDate.isBefore(MIN_START_DATE)) {
            throw new IllegalArgumentException(
                    "Start date is too far in the past (must be after " + MIN_START_DATE + ").");
        }
        if (assetId == null && (assetCategory == null || assetCategory.isBlank())) {
            throw new IllegalArgumentException("Either an asset or an asset category is required.");
        }
        if (serviceType == null || serviceType.isBlank()) {
            throw new IllegalArgumentException("Service type is required.");
        }
        if (responsibleParty == null || responsibleParty.isBlank()) {
            throw new IllegalArgumentException("Responsible party is required.");
        }
        if (responsibleRole == null || responsibleRole.isBlank()) {
            throw new IllegalArgumentException("Responsible role is required.");
        }

        MaintenanceSchedule schedule = new MaintenanceSchedule();
        if (assetId != null) {
            schedule.setAsset(assetService.findById(assetId));
        }
        schedule.setAssetCategory(clean(assetCategory));
        schedule.setServiceType(serviceType.trim());
        schedule.setFrequency(frequency);
        schedule.setStartDate(startDate);
        schedule.setNextDueDate(startDate);
        schedule.setResponsibleParty(responsibleParty.trim());
        schedule.setResponsibleRole(responsibleRole.trim());
        return scheduleRepository.save(schedule);
    }

    @Transactional
    public MaintenanceRecord recordCorrective(Long assetId,
                                              String issueDescription,
                                              String serviceProvider,
                                              BigDecimal maintenanceCost,
                                              LocalDate resolutionDate) {
        return recordCorrective(assetId, issueDescription, serviceProvider, maintenanceCost, resolutionDate, null, null);
    }

    @Transactional
    public MaintenanceRecord recordCorrective(Long assetId,
                                              String issueDescription,
                                              String serviceProvider,
                                              BigDecimal maintenanceCost,
                                              LocalDate resolutionDate,
                                              String requestedBy,
                                              String requestedByEmail) {
        MaintenanceRecord record = new MaintenanceRecord();
        record.setAsset(assetService.findById(assetId));
        record.setType(MaintenanceType.CORRECTIVE);
        record.setIssueDescription(issueDescription);
        record.setServiceProvider(clean(serviceProvider));
        record.setRequestedBy(hasText(requestedBy) ? requestedBy.trim() : "Asset Manager");
        record.setRequestedByEmail(requestedByEmail);
        record.setMaintenanceCost(maintenanceCost);
        record.setMaintenanceDate(resolutionDate == null ? LocalDate.now() : resolutionDate);
        record.setResolutionDate(resolutionDate);
        record.setStatus(resolutionDate == null ? MaintenanceStatus.OPEN : MaintenanceStatus.COMPLETED);
        MaintenanceRecord saved = recordRepository.save(record);

        // Notify asset managers of new maintenance request (only if OPEN - unresolved)
        if (record.getStatus() == MaintenanceStatus.OPEN) {
            try {
                List<SyncedUser> managers = syncedUserRepository.findByGroupName(",assetManager,");
                if (managers != null && !managers.isEmpty()) {
                    Asset asset = record.getAsset();
                    String subject = "Maintenance request: " + (asset != null ? asset.getName() : "Unknown Asset");
                    StringBuilder body = new StringBuilder();
                    body.append("Hello,\n\n");
                    body.append("An employee has submitted a maintenance request that requires attention:\n\n");
                    if (asset != null) {
                        body.append("Asset: ").append(asset.getName()).append(" (ID: ").append(asset.getId()).append(")\n");
                    }
                    body.append("Requested by: ").append(record.getRequestedBy()).append("\n");
                    body.append("Issue: ").append(issueDescription).append("\n");
                    if (serviceProvider != null && !serviceProvider.isBlank()) {
                        body.append("Service Provider: ").append(serviceProvider).append("\n");
                    }
                    body.append("\nYou can view and resolve this request here: ")
                            .append(appBaseUrl.replaceAll("/+$", ""))
                            .append("/admin/dashboard")
                            .append("\n\nRegards,\nFAMS Notification Service\n");

                    for (SyncedUser manager : managers) {
                        String to = manager.getEmail();
                        if (to == null || to.isBlank()) continue;
                        try {
                            emailService.sendEmail(to, subject, body.toString());
                        } catch (Exception ex) {
                            System.err.println("Failed to send maintenance notification to " + to + ": " + ex.getMessage());
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Failed to notify asset managers of maintenance request: " + ex.getMessage());
            }
        }

        return saved;
    }

    /**
     * Generates due tasks for every schedule whose nextDueDate is at or before today.
     * Catches up the full backlog of missed intervals (capped per schedule) so a schedule
     * that is weeks behind produces every missed task rather than just one.
     *
     * Each schedule is processed in its own try/catch so a single bad row cannot abort the
     * whole batch or roll back tasks already created for other schedules. Event publishing
     * to RabbitMQ is best-effort and fully decoupled from task persistence.
     *
     * @return total number of due tasks generated across all schedules
     */
    @Transactional
    public int generateDueTasks() {
        LocalDate today = LocalDate.now();
        int generated = 0;
        for (MaintenanceSchedule schedule : scheduleRepository.findByNextDueDateLessThanEqualOrderByNextDueDateAsc(today)) {
            try {
                generated += generateDueTasksForSchedule(schedule, today);
            } catch (Exception ex) {
                log.error("Failed to generate due tasks for schedule {}: {}", schedule.getId(), ex.getMessage(), ex);
            }
        }
        return generated;
    }

    private int generateDueTasksForSchedule(MaintenanceSchedule schedule, LocalDate today) {
        MaintenanceFrequency frequency = schedule.getFrequency();
        if (frequency == null) {
            log.warn("Schedule {} has no frequency; skipping due-task generation", schedule.getId());
            return 0;
        }
        LocalDate nextDue = schedule.getNextDueDate();
        if (nextDue == null) {
            log.warn("Schedule {} has null nextDueDate; skipping due-task generation", schedule.getId());
            return 0;
        }

        int generated = 0;
        int intervals = 0;
        while (!nextDue.isAfter(today)) {
            if (!taskRepository.existsByScheduleAndDueDate(schedule, nextDue)) {
                MaintenanceTask task = createTask(schedule, nextDue);
                generated++;
                publishDueEvent(task); // best-effort, never blocks persistence
            }
            nextDue = frequency.nextAfter(nextDue);
            if (++intervals >= MAX_BACKLOG) {
                log.warn("Schedule {} hit the backlog cap ({}); advancing nextDueDate to {} and stopping catch-up.",
                        schedule.getId(), MAX_BACKLOG, nextDue);
                break;
            }
        }

        schedule.setNextDueDate(nextDue);
        schedule.setStatus(generated > 0 ? MaintenanceStatus.DUE : MaintenanceStatus.SCHEDULED);
        scheduleRepository.save(schedule);
        return generated;
    }

    @Scheduled(cron = "0 0 7 * * *")
    public void generateDueTasksOnSchedule() {
        int generated = generateDueTasks();
        if (generated > 0) {
            log.info("Generated {} maintenance due task(s)", generated);
        }
    }

    /**
     * Scheduled reminder task that runs daily at 8 AM to send maintenance reminders.
     * Sends reminders for:
     * 1. Upcoming maintenance (nextDueDate within 7 days from today)
     * 2. Overdue maintenance (nextDueDate has passed)
     *
     * Reminders are sent once per day to each asset manager.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendMaintenanceReminders() {
        try {
            log.info("Starting maintenance reminder cycle");
            int remindersSent = 0;

            LocalDate today = LocalDate.now();
            LocalDate upcomingThreshold = today.plusDays(7);

            // Find all active schedules that need reminders
            List<MaintenanceSchedule> schedules = scheduleRepository.findByStatusOrderByNextDueDateAsc(MaintenanceStatus.DUE);

            for (MaintenanceSchedule schedule : schedules) {
                try {
                    LocalDate nextDue = schedule.getNextDueDate();
                    if (nextDue == null) continue;

                    boolean isUpcoming = !nextDue.isAfter(upcomingThreshold) && !nextDue.isBefore(today);
                    boolean isOverdue = nextDue.isBefore(today);

                    if (isUpcoming || isOverdue) {
                        if (shouldSendReminder(schedule)) {
                            sendMaintenanceReminder(schedule, isOverdue);
                            schedule.setLastReminderSentAt(LocalDateTime.now());
                            schedule.setRemindersSentCount(schedule.getRemindersSentCount() + 1);
                            scheduleRepository.save(schedule);
                            remindersSent++;
                        }
                    }
                } catch (Exception ex) {
                    log.error("Failed to send reminder for schedule {}: {}", schedule.getId(), ex.getMessage(), ex);
                }
            }

            // Also send reminders for overdue DUE tasks
            remindersSent += sendDueTaskReminders(today);

            if (remindersSent > 0) {
                log.info("Sent {} maintenance reminder(s)", remindersSent);
            }
        } catch (Exception ex) {
            log.error("Maintenance reminder cycle failed: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Determines if a reminder should be sent for this schedule.
     * Sends reminders once daily, starting from the day the maintenance is due.
     */
    private boolean shouldSendReminder(MaintenanceSchedule schedule) {
        LocalDateTime lastSent = schedule.getLastReminderSentAt();
        if (lastSent == null) {
            return true; // Never sent before
        }

        // Only send once per day (24 hours)
        LocalDateTime oneDayAgo = LocalDateTime.now().minusHours(24);
        return lastSent.isBefore(oneDayAgo);
    }

    /**
     * Sends a maintenance reminder email to asset managers.
     */
    private void sendMaintenanceReminder(MaintenanceSchedule schedule, boolean isOverdue) {
        try {
            List<SyncedUser> managers = syncedUserRepository.findByGroupName(",assetManager,");
            if (managers == null || managers.isEmpty()) {
                log.warn("No asset managers found to send maintenance reminder for schedule {}", schedule.getId());
                return;
            }

            Asset asset = schedule.getAsset();
            String assetInfo = (asset != null) ? asset.getName() + " (ID: " + asset.getId() + ")" : schedule.getAssetCategory();

            String subject;
            StringBuilder body = new StringBuilder();

            if (isOverdue) {
                subject = "OVERDUE: Maintenance Required - " + assetInfo;
                body.append("Hello Asset Manager,\n\n");
                body.append("⚠️  OVERDUE MAINTENANCE ALERT ⚠️\n\n");
                body.append("The following maintenance is OVERDUE and requires immediate attention:\n\n");
            } else {
                subject = "REMINDER: Scheduled Maintenance Due Soon - " + assetInfo;
                body.append("Hello Asset Manager,\n\n");
                body.append("📌 MAINTENANCE REMINDER 📌\n\n");
                body.append("The following maintenance is due within the next 7 days:\n\n");
            }

            body.append("Asset: ").append(assetInfo).append("\n");
            if (asset != null) {
                body.append("Asset Code: ").append(asset.getAssetCode()).append("\n");
                body.append("Category: ").append(asset.getCategory()).append("\n");
            }
            body.append("Service Type: ").append(schedule.getServiceType()).append("\n");
            body.append("Frequency: ").append(schedule.getFrequency()).append("\n");
            body.append("Due Date: ").append(schedule.getNextDueDate()).append("\n");
            body.append("Responsible Party: ").append(schedule.getResponsibleParty()).append("\n");
            body.append("Role: ").append(schedule.getResponsibleRole()).append("\n");

            if (isOverdue) {
                body.append("\n⏰ This maintenance is OVERDUE. Please take immediate action to schedule the maintenance.\n");
            }

            body.append("\nPlease access the system to view and manage this maintenance schedule: ");
            body.append(appBaseUrl.replaceAll("/+$", "")).append("/asset-manager/dashboard");
            body.append("\n\n--- REMINDER REMINDER ---\n");
            body.append("Reminder Number: ").append(schedule.getRemindersSentCount() + 1).append("\n");
            body.append("You will receive daily reminders until this maintenance is completed.\n\n");
            body.append("Regards,\n");
            body.append("FAMS Notification Service\n");

            for (SyncedUser manager : managers) {
                String to = manager.getEmail();
                if (to == null || to.isBlank()) continue;
                try {
                    emailService.sendEmail(to, subject, body.toString());
                    log.debug("Sent {} reminder to {}", isOverdue ? "overdue" : "upcoming", to);
                } catch (Exception ex) {
                    log.error("Failed to send maintenance reminder to {}: {}", to, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to send maintenance reminder for schedule {}: {}", schedule.getId(), ex.getMessage(), ex);
        }
    }

    /**
     * Sends reminders for DUE maintenance tasks that are overdue or approaching due date.
     */
    private int sendDueTaskReminders(LocalDate today) {
        int remindersSent = 0;
        LocalDate upcomingThreshold = today.plusDays(7);

        List<MaintenanceTask> dueTasks = taskRepository.findByStatusOrderByDueDateAsc(MaintenanceStatus.DUE);

        for (MaintenanceTask task : dueTasks) {
            try {
                LocalDate dueDate = task.getDueDate();
                if (dueDate == null) continue;

                boolean isUpcoming = !dueDate.isAfter(upcomingThreshold) && !dueDate.isBefore(today);
                boolean isOverdue = dueDate.isBefore(today);

                if (isUpcoming || isOverdue) {
                    if (shouldSendTaskReminder(task)) {
                        sendDueTaskReminder(task, isOverdue, today);
                        task.setLastReminderSentAt(LocalDateTime.now());
                        task.setRemindersSentCount(task.getRemindersSentCount() + 1);
                        taskRepository.save(task);
                        remindersSent++;
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to send reminder for task {}: {}", task.getId(), ex.getMessage(), ex);
            }
        }

        return remindersSent;
    }

    private boolean shouldSendTaskReminder(MaintenanceTask task) {
        LocalDateTime lastSent = task.getLastReminderSentAt();
        if (lastSent == null) {
            return true;
        }
        LocalDateTime oneDayAgo = LocalDateTime.now().minusHours(24);
        return lastSent.isBefore(oneDayAgo);
    }

    private void sendDueTaskReminder(MaintenanceTask task, boolean isOverdue, LocalDate today) {
        try {
            List<SyncedUser> managers = syncedUserRepository.findByGroupName(",assetManager,");
            if (managers == null || managers.isEmpty()) {
                return;
            }

            Asset asset = task.getAsset();
            String assetInfo = (asset != null) ? asset.getName() + " (ID: " + asset.getId() + ")" : task.getAssetCategory();
            LocalDate dueDate = task.getDueDate();
            long daysOverdue = isOverdue ? java.time.temporal.ChronoUnit.DAYS.between(dueDate, today) : 0;

            String subject;
            StringBuilder body = new StringBuilder();

            if (isOverdue) {
                subject = "OVERDUE: Maintenance Task - " + assetInfo;
                body.append("Hello Asset Manager,\n\n");
                body.append("⚠️  MAINTENANCE TASK OVERDUE ⚠️\n\n");
                body.append("The following maintenance task is OVERDUE by ").append(daysOverdue).append(" day(s):\n\n");
            } else {
                long daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate);
                subject = "REMINDER: Maintenance Task Due - " + assetInfo;
                body.append("Hello Asset Manager,\n\n");
                body.append("📌 MAINTENANCE TASK REMINDER 📌\n\n");
                body.append("The following maintenance task is due in ").append(daysUntilDue).append(" day(s):\n\n");
            }

            body.append("Asset: ").append(assetInfo).append("\n");
            if (asset != null) {
                body.append("Asset Code: ").append(asset.getAssetCode()).append("\n");
                body.append("Category: ").append(asset.getCategory()).append("\n");
            }
            body.append("Service Type: ").append(task.getServiceType()).append("\n");
            body.append("Due Date: ").append(dueDate).append("\n");
            body.append("Status: ").append(task.getStatus()).append("\n");
            body.append("Responsible Party: ").append(task.getResponsibleParty()).append("\n");
            body.append("Role: ").append(task.getResponsibleRole()).append("\n");

            if (isOverdue) {
                body.append("\n⏰ This task is ").append(daysOverdue).append(" day(s) overdue. Please complete it immediately.\n");
            }

            body.append("\nPlease access the system to complete this maintenance task: ");
            body.append(appBaseUrl.replaceAll("/+$", "")).append("/asset-manager/dashboard");
            body.append("\n\n--- REMINDER NUMBER: ").append(task.getRemindersSentCount() + 1).append(" ---\n");
            body.append("You will receive daily reminders until this task is marked as completed.\n\n");
            body.append("Regards,\n");
            body.append("FAMS Notification Service\n");

            for (SyncedUser manager : managers) {
                String to = manager.getEmail();
                if (to == null || to.isBlank()) continue;
                try {
                    emailService.sendEmail(to, subject, body.toString());
                } catch (Exception ex) {
                    log.error("Failed to send task reminder to {}: {}", to, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to send maintenance task reminder for task {}: {}", task.getId(), ex.getMessage(), ex);
        }
    }

    /**
     * Resolves a DUE preventive maintenance task: records the completed work as a PREVENTIVE
     * MaintenanceRecord (linked to the schedule and asset for full traceability) and marks the
     * task COMPLETED. Idempotent-safe: a task that is already resolved cannot be resolved again.
     *
     * @throws IllegalStateException if the task does not exist or is not in DUE status
     */
    @Transactional
    public void resolveTask(Long taskId,
                            String serviceProvider,
                            BigDecimal maintenanceCost,
                            LocalDate resolutionDate,
                            String notes) {
        MaintenanceTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Maintenance task not found: " + taskId));
        if (task.getStatus() != MaintenanceStatus.DUE) {
            throw new IllegalStateException(
                    "Only DUE tasks can be resolved (task " + taskId + " is " + task.getStatus() + ").");
        }

        MaintenanceSchedule schedule = task.getSchedule();
        LocalDate resolvedOn = resolutionDate == null ? LocalDate.now() : resolutionDate;

        MaintenanceRecord record = new MaintenanceRecord();
        record.setAsset(task.getAsset());
        record.setSchedule(schedule);
        record.setType(MaintenanceType.PREVENTIVE);
        record.setIssueDescription(clean(notes) == null ? "Preventive maintenance completed" : clean(notes));
        record.setServiceProvider(clean(serviceProvider));
        record.setRequestedBy(schedule == null ? "System" : schedule.getResponsibleParty());
        record.setMaintenanceCost(maintenanceCost);
        record.setMaintenanceDate(resolvedOn);
        record.setResolutionDate(resolutionDate);
        record.setStatus(MaintenanceStatus.COMPLETED);
        recordRepository.save(record);

        task.setStatus(MaintenanceStatus.COMPLETED);
        task.setResolutionCost(maintenanceCost);
        task.setResolutionDate(resolutionDate);
        taskRepository.save(task);

        log.info("Resolved maintenance task {} as PREVENTIVE record (schedule {})",
                taskId, schedule == null ? "n/a" : schedule.getId());
    }

    /**
     * Resolves an inbound employee maintenance request (an OPEN CORRECTIVE MaintenanceRecord).
     * Captures the resolution details and marks the record COMPLETED so it leaves the pending
     * request queue and the dashboard notification count.
     *
     * @throws IllegalStateException if the record does not exist or is not in OPEN status
     */
    @Transactional
    public void resolveRequest(Long recordId,
                               String serviceProvider,
                               BigDecimal maintenanceCost,
                               LocalDate resolutionDate,
                               String notes) {
        MaintenanceRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalStateException("Maintenance request not found: " + recordId));
        if (record.getStatus() != MaintenanceStatus.OPEN) {
            throw new IllegalStateException(
                    "Only OPEN requests can be resolved (request " + recordId + " is " + record.getStatus() + ").");
        }

        LocalDate resolvedOn = resolutionDate == null ? LocalDate.now() : resolutionDate;
        record.setServiceProvider(clean(serviceProvider));
        record.setMaintenanceCost(maintenanceCost);
        record.setResolutionDate(resolutionDate);
        record.setMaintenanceDate(resolvedOn);
        if (clean(notes) != null) {
            record.setIssueDescription(record.getIssueDescription() + " | Resolution: " + clean(notes));
        }
        record.setStatus(MaintenanceStatus.COMPLETED);
        MaintenanceRecord saved = recordRepository.save(record);

        // Notify the requester of resolution
        try {
            String to = record.getRequestedByEmail();
            if (to != null && !to.isBlank()) {
                System.out.println("[MAINTENANCE RESOLVE] Sending resolution email to stored address: " + to);
                    Asset asset = record.getAsset();
                    String subject = "Your maintenance request has been resolved: " + (asset != null ? asset.getName() : "Asset");
                    StringBuilder body = new StringBuilder();
                    body.append("Hello,\n\n");
                    body.append("Good news — your maintenance request has been resolved:\n\n");
                    if (asset != null) {
                        body.append("Asset: ").append(asset.getName()).append(" (ID: ").append(asset.getId()).append(")\n");
                    }
                    body.append("Original Issue: ").append(record.getIssueDescription()).append("\n");
                    if (serviceProvider != null && !serviceProvider.isBlank()) {
                        body.append("Service Provider: ").append(serviceProvider).append("\n");
                    }
                    if (maintenanceCost != null) {
                        body.append("Cost: ").append(maintenanceCost).append("\n");
                    }
                    body.append("Resolution Date: ").append(resolutionDate).append("\n\n");
                    body.append("You can view details here: ")
                            .append(appBaseUrl.replaceAll("/+$", ""))
                            .append("/employee/dashboard")
                            .append("\n\nRegards,\nFAMS Notification Service\n");
                    try {
                        emailService.sendEmail(to, subject, body.toString());
                    } catch (Exception ex) {
                        System.err.println("Failed to send maintenance resolution notification to " + to + ": " + ex.getMessage());
                    }
            } else {
                System.out.println("[MAINTENANCE RESOLVE] No email stored for requester (recordId=" + record.getId() + ")");
            }
        } catch (Exception ex) {
            System.err.println("Failed to send maintenance resolution notification: " + ex.getMessage());
        }

        log.info("Resolved employee maintenance request {} (asset {})",
                recordId, record.getAsset() == null ? "n/a" : record.getAsset().getId());
    }

    private MaintenanceTask createTask(MaintenanceSchedule schedule, LocalDate dueDate) {
        MaintenanceTask task = new MaintenanceTask();
        task.setSchedule(schedule);
        task.setAsset(schedule.getAsset());
        task.setAssetCategory(schedule.getAssetCategory());
        task.setServiceType(schedule.getServiceType());
        task.setDueDate(dueDate);
        task.setResponsibleParty(schedule.getResponsibleParty());
        task.setResponsibleRole(schedule.getResponsibleRole());
        task.setStatus(MaintenanceStatus.DUE);
        task.setEventPublished(false);
        return taskRepository.save(task);
    }

    /**
     * Best-effort publish of a due-task notification. Must never throw: a missing or
     * unavailable RabbitMQ broker must not prevent the task from being created or persisted.
     * The eventPublished flag is updated separately and best-effort only.
     */
    private void publishDueEvent(MaintenanceTask task) {
        try {
            Asset asset = task.getAsset();
            MaintenanceDueEvent event = new MaintenanceDueEvent(
                    task.getId(),
                    task.getSchedule().getId(),
                    asset == null ? null : asset.getId(),
                    asset == null ? null : asset.getAssetCode(),
                    asset == null ? null : asset.getName(),
                    task.getAssetCategory(),
                    task.getServiceType(),
                    task.getDueDate(),
                    task.getResponsibleParty(),
                    task.getResponsibleRole());
            rabbitTemplate.convertAndSend(
                    MaintenanceMessagingConfig.EXCHANGE,
                    MaintenanceMessagingConfig.DUE_ROUTING_KEY,
                    event);
            markEventPublished(task.getId());
        } catch (Exception ex) {
            // Messaging is optional; the due task already exists and is what the user sees.
            log.warn("Maintenance due event for task {} was not published (broker unavailable?): {}",
                    task.getId(), ex.getMessage());
        }
    }

    @Transactional
    public void markEventPublished(Long taskId) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setEventPublished(true);
            task.setEventPublishedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
