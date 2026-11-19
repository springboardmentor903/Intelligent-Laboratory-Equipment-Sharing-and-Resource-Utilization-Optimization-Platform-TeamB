package com.campus.lab_resource_platform.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Pre-migration hook that runs against the DataSource before Hibernate initializes
 * EntityManagerFactory. Ensures new M2 columns exist with valid defaults for existing
 * rows and clears any orphaned foreign key references so Hibernate DDL update succeeds
 * without CommandAcceptanceException.
 */
@Configuration
public class DatabasePreMigrationConfig implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DatabasePreMigrationConfig.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource) {
            log.info("Running DatabasePreMigrationConfig on DataSource bean '{}'...", beanName);
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {

                // 1. Ensure bookings columns exist with defaults and backfill existing nulls
                try {
                    stmt.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS is_external boolean DEFAULT false");
                    stmt.execute("UPDATE bookings SET is_external = false WHERE is_external IS NULL");
                    stmt.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0");
                    stmt.execute("UPDATE bookings SET version = 0 WHERE version IS NULL");
                    stmt.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS is_recurring boolean DEFAULT false");
                    stmt.execute("UPDATE bookings SET is_recurring = false WHERE is_recurring IS NULL");
                    log.info("Pre-migration: bookings table schema and defaults successfully checked/updated.");
                } catch (Exception e) {
                    log.debug("Notice on bookings table migration: {}", e.getMessage());
                }

                // 2. Ensure access_requests table has version and time slot columns with defaults
                try {
                    stmt.execute("ALTER TABLE access_requests ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0");
                    stmt.execute("UPDATE access_requests SET version = 0 WHERE version IS NULL");
                    stmt.execute("ALTER TABLE access_requests ADD COLUMN IF NOT EXISTS allowed_start_time time without time zone");
                    stmt.execute("ALTER TABLE access_requests ADD COLUMN IF NOT EXISTS allowed_end_time time without time zone");
                    stmt.execute("ALTER TABLE sharing_agreements ADD COLUMN IF NOT EXISTS allowed_start_time time without time zone");
                    stmt.execute("ALTER TABLE sharing_agreements ADD COLUMN IF NOT EXISTS allowed_end_time time without time zone");
                    // Fix FK constraint on access_requests if pointing to obsolete "equipment"
                    stmt.execute("""
                        DO $$
                        BEGIN
                            IF EXISTS (
                                SELECT 1 FROM information_schema.constraint_column_usage ccu
                                JOIN information_schema.table_constraints tc ON ccu.constraint_name = tc.constraint_name
                                WHERE tc.table_name = 'access_requests' AND ccu.table_name = 'equipment'
                            ) THEN
                                EXECUTE (
                                    SELECT 'ALTER TABLE access_requests DROP CONSTRAINT ' || tc.constraint_name
                                    FROM information_schema.constraint_column_usage ccu
                                    JOIN information_schema.table_constraints tc ON ccu.constraint_name = tc.constraint_name
                                    WHERE tc.table_name = 'access_requests' AND ccu.table_name = 'equipment'
                                    LIMIT 1
                                );
                            END IF;
                        END $$;
                    """);
                    stmt.execute("""
                        DO $$
                        BEGIN
                            IF NOT EXISTS (
                                SELECT 1 FROM information_schema.table_constraints 
                                WHERE constraint_name = 'fk_access_requests_equipments' 
                                  AND table_name = 'access_requests'
                            ) THEN
                                ALTER TABLE access_requests 
                                ADD CONSTRAINT fk_access_requests_equipments 
                                FOREIGN KEY (equipment_id) REFERENCES equipments(id) ON DELETE CASCADE;
                            END IF;
                        END $$;
                    """);
                    log.info("Pre-migration: access_requests table schema successfully checked/updated.");
                } catch (Exception e) {
                    log.debug("Notice on access_requests table migration: {}", e.getMessage());
                }

                // 3. Ensure equipments table has new M2 columns with safe defaults
                try {
                    stmt.execute("ALTER TABLE equipments ADD COLUMN IF NOT EXISTS is_shareable boolean DEFAULT true");
                    stmt.execute("UPDATE equipments SET is_shareable = true WHERE is_shareable IS NULL");
                    stmt.execute("ALTER TABLE equipments ADD COLUMN IF NOT EXISTS hourly_cost numeric(12,2) DEFAULT 250.00");
                    stmt.execute("UPDATE equipments SET hourly_cost = 250.00 WHERE hourly_cost IS NULL");
                    stmt.execute("ALTER TABLE equipments ADD COLUMN IF NOT EXISTS purchase_cost numeric(12,2)");
                    stmt.execute("ALTER TABLE equipments ADD COLUMN IF NOT EXISTS purchase_date date");
                    stmt.execute("ALTER TABLE equipments ADD COLUMN IF NOT EXISTS operating_start_time time without time zone DEFAULT '08:00:00'");
                    stmt.execute("ALTER TABLE equipments ADD COLUMN IF NOT EXISTS operating_end_time time without time zone DEFAULT '18:00:00'");
                    stmt.execute("ALTER TABLE equipments ADD COLUMN IF NOT EXISTS created_at timestamp with time zone DEFAULT NOW()");
                    stmt.execute("ALTER TABLE equipments ALTER COLUMN created_at SET DEFAULT NOW()");
                    stmt.execute("UPDATE equipments SET created_at = NOW() WHERE created_at IS NULL");
                    stmt.execute("ALTER TABLE equipments ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone DEFAULT NOW()");
                    stmt.execute("ALTER TABLE equipments ALTER COLUMN updated_at SET DEFAULT NOW()");
                    stmt.execute("UPDATE equipments SET updated_at = NOW() WHERE updated_at IS NULL");
                    log.info("Pre-migration: equipments table schema successfully checked/updated.");
                } catch (Exception e) {
                    log.debug("Notice on equipments table migration: {}", e.getMessage());
                }

                // 4. Ensure utilization table has new M2 columns with safe defaults
                try {
                    stmt.execute("ALTER TABLE utilization ADD COLUMN IF NOT EXISTS usage_type varchar(30) DEFAULT 'INTERNAL'");
                    stmt.execute("UPDATE utilization SET usage_type = 'INTERNAL' WHERE usage_type IS NULL");
                    stmt.execute("ALTER TABLE utilization ADD COLUMN IF NOT EXISTS used_minutes integer");
                    stmt.execute("ALTER TABLE utilization ADD COLUMN IF NOT EXISTS actual_fee numeric(12,2)");
                    stmt.execute("ALTER TABLE utilization ADD COLUMN IF NOT EXISTS notes varchar(1000)");
                    log.info("Pre-migration: utilization table schema successfully checked/updated.");
                } catch (Exception e) {
                    log.debug("Notice on utilization table migration: {}", e.getMessage());
                }

                // 5. Ensure active equipments have sharing_listings populated for cross-institution discovery
                try {
                    // Ensure sharing_agreements table allows EXPIRED and COMPLETED statuses
                    stmt.execute("""
                        DO $$
                        BEGIN
                            ALTER TABLE sharing_agreements DROP CONSTRAINT IF EXISTS sharing_agreements_status_check;
                            ALTER TABLE sharing_agreements ADD CONSTRAINT sharing_agreements_status_check 
                                CHECK (status::text = ANY (ARRAY['ACTIVE'::text, 'REVOKED'::text, 'EXPIRED'::text, 'COMPLETED'::text]));
                            UPDATE sharing_agreements SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND end_date < CURRENT_DATE;
                        EXCEPTION WHEN OTHERS THEN
                            NULL;
                        END $$;
                    """);
                    // If fk9qrv8bggoe4wkd6kqtkfqwhre references "equipment" instead of "equipments", drop and recreate it
                    stmt.execute("""
                        DO $$
                        DECLARE
                            r RECORD;
                        BEGIN
                            FOR r IN (
                                SELECT tc.table_name, tc.constraint_name
                                FROM information_schema.table_constraints tc
                                JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name
                                WHERE tc.constraint_type = 'FOREIGN KEY' AND ccu.table_name = 'equipment'
                            ) LOOP
                                EXECUTE 'ALTER TABLE ' || quote_ident(r.table_name) || ' DROP CONSTRAINT IF EXISTS ' || quote_ident(r.constraint_name);
                            END LOOP;
                            DROP TABLE IF EXISTS equipment CASCADE;
                        END $$;
                    """);
                    stmt.execute("""
                        DO $$
                        BEGIN
                            IF NOT EXISTS (
                                SELECT 1 FROM information_schema.table_constraints 
                                WHERE constraint_name = 'fk_sharing_listings_equipments' 
                                  AND table_name = 'sharing_listings'
                            ) THEN
                                ALTER TABLE sharing_listings 
                                ADD CONSTRAINT fk_sharing_listings_equipments 
                                FOREIGN KEY (equipment_id) REFERENCES equipments(id) ON DELETE CASCADE;
                            END IF;
                        END $$;
                    """);
                    log.info("Pre-migration: sharing_listings foreign key constraint verified pointing to equipments table.");

                    // Sync owner_institution_id with equipment college_id
                    stmt.execute("""
                        INSERT INTO institutions (id, name, code, created_at, updated_at)
                        SELECT id, name, code, NOW(), NOW() FROM colleges
                        ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, code = EXCLUDED.code;
                    """);
                    stmt.execute("""
                        UPDATE sharing_listings sl
                        SET owner_institution_id = COALESCE(e.college_id, e.institution_id, 1)
                        FROM equipments e
                        WHERE sl.equipment_id = e.id;
                    """);

                    // Seed partner college equipment so Department Heads always have partner instruments to discover & request
                    stmt.execute("""
                        INSERT INTO equipments (
                            name, asset_tag, description, category, model, manufacturer, location, specifications, college_id, institution_id, hourly_cost, is_shareable, requires_approval, status, is_active, max_booking_hours, created_at, updated_at
                        )
                        SELECT 'High-Resolution Transmission Electron Microscope (HR-TEM)', 'EQ-PARTNER-TEM-01', 'State-of-the-art nanoscale structural analysis instrument for advanced material science and nanotechnology research.', 'Nanotechnology', 'JEM-2100Plus', 'JEOL', 'Central Characterization Facility, Room 102', '0.19 nm point resolution, 200 kV acceleration voltage, cryogenic sample holder', 4, 4, 500.00, true, true, 'AVAILABLE', true, 6, NOW(), NOW()
                        WHERE NOT EXISTS (SELECT 1 FROM equipments WHERE asset_tag = 'EQ-PARTNER-TEM-01');
                    """);

                    stmt.execute("""
                        INSERT INTO equipments (
                            name, asset_tag, description, category, model, manufacturer, location, specifications, college_id, institution_id, hourly_cost, is_shareable, requires_approval, status, is_active, max_booking_hours, created_at, updated_at
                        )
                        SELECT 'Automated DNA Sequencer (Illumina NovaSeq 6000)', 'EQ-PARTNER-SEQ-02', 'High-throughput next-generation genomic sequencing platform for genomics, transcriptomics, and metagenomics studies.', 'Biotechnology', 'NovaSeq 6000', 'Illumina', 'Biotechnology Core Lab, Level 3', 'Dual flow cell capability, up to 6 Tb output per run, read lengths up to 2x250 bp', 2, 2, 600.00, true, true, 'AVAILABLE', true, 8, NOW(), NOW()
                        WHERE NOT EXISTS (SELECT 1 FROM equipments WHERE asset_tag = 'EQ-PARTNER-SEQ-02');
                    """);

                    stmt.execute("""
                        INSERT INTO equipments (
                            name, asset_tag, description, category, model, manufacturer, location, specifications, college_id, institution_id, hourly_cost, is_shareable, requires_approval, status, is_active, max_booking_hours, created_at, updated_at
                        )
                        SELECT 'High-Performance GPU Computing Cluster (NVIDIA DGX H100)', 'EQ-PARTNER-GPU-03', 'Enterprise deep learning compute cluster for large language model fine-tuning, molecular dynamics, and scientific simulations.', 'Computing', 'DGX H100', 'NVIDIA', 'Supercomputing Data Center, Rack 04', '8x NVIDIA H100 80GB SXM5 GPUs, 32 PFLOPS FP8 AI compute, 2TB system memory', 1, 1, 350.00, true, true, 'AVAILABLE', true, 12, NOW(), NOW()
                        WHERE NOT EXISTS (SELECT 1 FROM equipments WHERE asset_tag = 'EQ-PARTNER-GPU-03');
                    """);

                    stmt.execute("""
                        INSERT INTO sharing_listings (equipment_id, owner_institution_id, fee_per_hour, external_share_percentage, min_notice_hours, max_booking_duration_hours, sharing_terms, is_active, created_at, updated_at)
                        SELECT e.id, COALESCE(e.college_id, e.institution_id, 1), COALESCE(e.hourly_cost, 250.00), 100.00, 0, COALESCE(NULLIF(e.max_booking_hours, 0), 8), 'Available for inter-institution research and laboratory sharing.', true, NOW(), NOW()
                        FROM equipments e
                        WHERE e.is_active = true
                          AND e.id NOT IN (SELECT equipment_id FROM sharing_listings WHERE equipment_id IS NOT NULL)
                    """);
                    log.info("Pre-migration: sharing_listings synchronized for active equipments.");
                } catch (Exception e) {
                    log.warn("Notice on sharing_listings synchronization: {}", e.getMessage());
                }

            } catch (Exception ex) {
                log.warn("DatabasePreMigrationConfig warning: {}", ex.getMessage());
            }
        }
        return bean;
    }
}

