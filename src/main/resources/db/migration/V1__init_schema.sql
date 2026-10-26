-- NHCDC WhatsApp chatbot - Phase 1 schema and starter content.
-- Single baseline migration: lead form = centre, patient name, patient
-- number, DOB, concern, referral doctor, consent (per the NHCDC
-- requirements checklist of 14 Aug 2026).
--
-- Once this has run against a real database, never edit it - add V2, V3, ...

-- ----------------------------------------------------------------- centres
CREATE TABLE centres (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    address VARCHAR(500) NOT NULL,
    contact_number VARCHAR(255),
    maps_link VARCHAR(1000),
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

-- ---------------------------------------------------------------- services
CREATE TABLE service_offerings (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

-- ------------------------------------------------------------------- leads
CREATE SEQUENCE lead_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE leads (
    id BIGSERIAL PRIMARY KEY,
    lead_code VARCHAR(255) NOT NULL UNIQUE,
    whatsapp_number VARCHAR(255) NOT NULL,
    patient_name VARCHAR(255),
    patient_number VARCHAR(20),
    date_of_birth DATE,
    concern VARCHAR(2000),
    centre_id BIGINT REFERENCES centres (id),
    service_id BIGINT REFERENCES service_offerings (id),
    referral_doctor VARCHAR(255),
    consent_given BOOLEAN NOT NULL DEFAULT FALSE,
    consent_at TIMESTAMPTZ,
    consent_text_snapshot VARCHAR(2000),
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    source VARCHAR(255) NOT NULL DEFAULT 'WhatsApp Chatbot',
    -- false until the lead is in the Google Sheet; LeadSheetSyncJob retries until it is
    sheet_synced BOOLEAN NOT NULL DEFAULT FALSE,
    sheet_sync_attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_leads_whatsapp_number ON leads (whatsapp_number);
CREATE INDEX idx_leads_centre_id ON leads (centre_id);
CREATE INDEX idx_leads_duplicate_check ON leads (whatsapp_number, centre_id, status, created_at);
CREATE INDEX idx_leads_sheet_pending ON leads (created_at) WHERE sheet_synced = FALSE;

-- ---------------------------------------------------- conversation sessions
CREATE TABLE conversation_sessions (
    id BIGSERIAL PRIMARY KEY,
    whatsapp_number VARCHAR(255) NOT NULL UNIQUE,
    current_step VARCHAR(50) NOT NULL DEFAULT 'MAIN_MENU',
    pending_lead_field VARCHAR(50) DEFAULT 'NONE',
    tagged_centre_id BIGINT,
    tagged_service_id BIGINT,
    draft_data_json TEXT,
    last_activity_at TIMESTAMPTZ,
    -- true once the "we haven't heard from you" reminder was sent for this quiet period
    idle_reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ
);

-- lets SessionIdleJob find quiet mid-conversation sessions without scanning every user
CREATE INDEX idx_sessions_idle_check ON conversation_sessions (last_activity_at) WHERE current_step <> 'MAIN_MENU';

-- ------------------------------------------------------------ bot settings
CREATE TABLE bot_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    description VARCHAR(255)
);

-- ======================================================== STARTER CONTENT
-- Service text below are still pending NHCDC approval ("pending" /
-- "on website" in the checklist). Replace them through a new migration or
-- direct updates once approved.

-- Centres: names, addresses and phone numbers as supplied by NHCDC.
INSERT INTO centres (name, address, contact_number, maps_link, sort_order, active) VALUES
('Goregaon (East)', 'Saira Mansion, Near Pahadi School Road Number 2, Jay Prakash Nagar, Goregaon East, Mumbai - 400063', '+91 9819162191', 'https://maps.app.goo.gl/3seUFT3W1cWR1PiPA', 1, TRUE),
('Goregaon (West)', 'Unit 10, 2nd Floor, Techniplex 2, Veer Savarkar Flyover, Malad, Liliya Nagar, Goregaon West, Mumbai - 400062', '+91 7506171240', 'https://maps.app.goo.gl/UxQwUo91vjL6qfEj7', 2, TRUE),
('Borivali (West)', '3-B-185, 2nd Floor Ganjawala Apartment Co-op Hsg Society, Ganjawala Lane, Borivali West, Mumbai - 400092', '+91 8452940605', 'https://maps.app.goo.gl/SRTxX7PWht5ma3W38', 3, TRUE),
('Bhandup', 'Pawar Public School Basement, Opp. Dreams Mall, Off LBS Marg, Bhandup West, Mumbai - 400078', '+91 9769669332', 'https://maps.app.goo.gl/jBLPmipdYSNABogM8', 4, TRUE),
('Mahim', '101, First floor, Svarna Rose Mary, Plot No 03, LJ, Cross Rd no 2, Mahim West, Mumbai - 400016', '+91 7045352552', 'https://maps.app.goo.gl/k21MGDDxDfbBZeiy7', 5, TRUE),
('Dadar', 'Sri Guru Singh Sabha, 2nd floor, Dr. Babasaheb Ambedkar Road, Opp Chitra Cinema, Dadar East, Mumbai', '+91 7506171230', 'https://maps.app.goo.gl/dEwpSMNWQHWhWdkA7', 6, TRUE),
('Vasai', '203, Mukesh Apartment, Vasai Station Rd, above, Madhuram Hotel, Manickpur, Vasai West, Palghar - 401202', '+91 9321164726', 'https://maps.app.goo.gl/yxrJB2iW8GLGaA3DA', 7, TRUE),
('Vadodara', '301, Infinity Arcade Before Pratap nagar Bridge, Baroda Dairy Cir, ONGC, Vadodara, Gujarat 390004', '+91 7738155667', 'https://maps.app.goo.gl/RjHtFZ11JRaRgrm89', 8, TRUE),
('Hyderabad - Gachibowli', 'Ankura Hospital, Sy no 8 & 163, Narsing Nanakramguda Service Rd, opposite to Concu, near to Karachi bakery, Chaitanya Enclave, Gachibowli, Khajaguda, Makthakousarali, Telangana 500008', '+91 8976964689', 'https://maps.app.goo.gl/WDRGsGPSgRFJe95S9', 9, TRUE),
('Hyderabad - KPHB', 'Ankura Hospital, Kukatpally Housing Board Colony, K P H B Phase 7, Kukatpally, Hyderabad, Telangana 500085', '+91 8976964689', 'https://maps.app.goo.gl/xnzrg47cirwr3Lyf9', 10, TRUE),
('E-nable (Online)', '203, Mukesh Apartment, Vasai Station Rd, above, Madhuram Hotel, Manickpur, Vasai West, Palghar - 401202', '+91 9819224415', 'https://maps.app.goo.gl/yxrJB2iW8GLGaA3DA', 11, TRUE);

-- Services: title only (the real NHCDC list).
INSERT INTO service_offerings (name, sort_order, active) VALUES
('Delayed motor development', 1, TRUE),
('Communication speech concerns', 2, TRUE),
('Behavioural concerns', 3, TRUE),
('Academic concerns', 4, TRUE),
('Neurodevelopmental and behavioral disorders', 5, TRUE),
('Specific learning disability (SLD)', 6, TRUE),
('Autism spectrum disorder (ASD)', 7, TRUE),
('Attention deficit hyperactivity disorder (ADHD)', 8, TRUE),
('Intellectual disability', 9, TRUE),
('Cerebral palsy (CP)', 10, TRUE),
('Neuromuscular disorders', 11, TRUE),
('Muscular dystrophy', 12, TRUE);

-- Bot texts and contact details (editable without a code change).
INSERT INTO bot_settings (setting_key, setting_value, description) VALUES
('welcome_message', E'*Welcome to New Horizons Child Development Centre!*\n\nWe''re delighted you reached out. Our team supports children and their families with care and expertise.\n\nHow can we help you and your child today?', 'First message shown with the main menu'),
('thank_you_message', E'*Thank you!* Your request has been received.\n\nOur team will contact you shortly.', 'Sent after a lead is saved (the reference number is added automatically)'),
('duplicate_message', E'*We already have your recent request.*\n\nOur team will contact you shortly.', 'Sent when the same number already has an open lead for the same centre within the duplicate window'),
('fallback_message', E'Sorry, I didn''t quite get that — I can only help with the options in the menu.\n\nPlease tap an option below, or talk to our team and we''ll be happy to help.', 'Sent when the user types something the bot cannot handle'),
('consent_text', E'I confirm that I am the parent/legal guardian of the patient. I agree that New Horizons Child Development Centre may store the details I have shared and contact me on phone or WhatsApp about this enquiry. My details will be used only for this purpose.', 'Consent shown with the review card before saving a lead (DRAFT - needs NHCDC approval)'),
('idle_reminder_message', E'*Are you still there?*\nWe haven''t heard from you for a few minutes.\n\nYou can carry on from where you left off below, or type *menu* to start over.', 'Sent once when a parent goes quiet part-way through a menu or the form (the step they were on is added automatically)'),
('session_expired_message', E'*Your session has ended* because we didn''t hear back for a while, and the details you had entered were not saved.\n\nNo worries — please start fresh from the menu below', 'Sent when a quiet conversation times out and is reset to the main menu'),
('contact_message', E'*Contact Our Team*\n\nWe''d love to hear from you. Reach us at:\n\nPhone / WhatsApp:\n +91 8976964693\nEmail:\n info@enablemychild.org\n\nTo request a callback instead, choose *Book a Consultation* from the menu.', 'Contact details sent for Talk to Our Team / Contact Us');
