--
-- V1: the schema as the application's entities define it.
--
-- Generated from the JPA model rather than hand-written, so `ddl-auto=validate`
-- passes against it exactly. Everything after this is a hand-written, reviewed
-- migration - Hibernate never changes a production schema again.
--
-- An existing database (a developer's, or an environment created before Flyway
-- arrived) is adopted rather than rebuilt: `baseline-on-migrate` marks this
-- version as already applied instead of running it. That is why this file must
-- never be edited once it has run anywhere - Flyway checksums it, and a changed
-- checksum fails the next startup.
--

create table audit_logs (
        occurred_at timestamp(6) with time zone not null,
        candidate_id uuid,
        id uuid not null,
        actor_type varchar(30) not null check (actor_type in ('HR','CANDIDATE','SYSTEM')),
        event_type varchar(60) not null check (event_type in ('CANDIDATE_CREATED','CANDIDATE_UPDATED','PROFILE_CORRECTED','INVITATION_GENERATED','INVITATION_RESENT','PORTAL_TOKEN_REGENERATED','PORTAL_LINK_VIEWED','PORTAL_ACCESSED','VERIFICATION_CODE_SENT','VERIFICATION_SUCCEEDED','VERIFICATION_FAILED','PROFILE_SUBMITTED','PROFILE_UPDATED','SUBMITTED_FOR_REVIEW','DOCUMENT_UPLOADED','DOCUMENT_REUPLOADED','DOCUMENT_VERIFIED','DOCUMENT_REJECTED','DOCUMENT_DOWNLOADED','DOCUMENT_REOPENED','DOCUMENTS_APPROVED','CANDIDATE_NOTIFIED','REQUIRED_DOCUMENT_ADDED','REQUIRED_DOCUMENT_MANDATORY_CHANGED','OFFER_UPLOADED','OFFER_SIGNATURE_FIELDS_SET','OFFER_SENT','OFFER_VIEWED','OFFER_ACCEPTED','ONBOARDING_COMPLETED')),
        ip_address varchar(60),
        actor varchar(180) not null,
        document_reference varchar(200),
        metadata varchar(4000),
        primary key (id)
    );

    create table candidate_documents (
        version integer not null,
        reviewed_at timestamp(6) with time zone,
        size_bytes bigint not null,
        uploaded_at timestamp(6) with time zone not null,
        candidate_id uuid not null,
        id uuid not null,
        status varchar(30) not null check (status in ('PENDING','SUBMITTED','VERIFIED','REJECTED')),
        education_course varchar(40) check (education_course in ('INTERMEDIATE','DIPLOMA','POLYTECHNIC','ITI','OTHER_SECONDARY','B_TECH','B_E','B_SC','B_COM','BCA','BBA','BA','B_PHARM','OTHER_UG','M_TECH','M_E','M_SC','MCA','MBA','M_COM','MA','OTHER_PG','PHD')),
        custom_type_code varchar(60),
        document_type varchar(60) not null check (document_type in ('SSC_CERTIFICATE','SECONDARY_EDUCATION_CERTIFICATE','HIGHER_EDUCATION_PROVISIONAL','HIGHER_EDUCATION_ORIGINAL_DEGREE','HIGHER_EDUCATION_MARKSHEET','AADHAAR_ID','PAN_CARD','PASSPORT_PHOTO','ADDRESS_PROOF','EXPERIENCE_CERTIFICATE','RELIEVING_LETTER','PREVIOUS_OFFER_LETTER','PAYSLIP_MONTH_1','PAYSLIP_MONTH_2','PAYSLIP_MONTH_3','PAYSLIP_MONTH_4','PAYSLIP_MONTH_5','PAYSLIP_MONTH_6','PAYSLIPS','BANK_DETAILS','OTHER','EDUCATION_CERTIFICATE','INTERMEDIATE_CERTIFICATE','DIPLOMA_CERTIFICATE','UG_DEGREE_CERTIFICATE','PG_DEGREE_CERTIFICATE')),
        content_type varchar(120),
        reviewed_by varchar(180),
        original_filename varchar(260) not null,
        storage_key varchar(400) not null,
        reject_reason varchar(600),
        primary key (id),
        constraint uk_document_candidate_type unique (candidate_id, document_type)
    );

    create table candidate_field_settings (
        enabled boolean not null,
        required boolean not null,
        field varchar(60) not null check (field in ('FULL_NAME_AS_PER_AADHAAR','PERSONAL_EMAIL','CONTACT_NUMBER','ALTERNATE_CONTACT_NUMBER','DATE_OF_BIRTH','GENDER','FATHERS_NAME','PERMANENT_ADDRESS','BLOOD_GROUP','AADHAAR_NUMBER','PAN_NUMBER','EMERGENCY_CONTACT_NAME','EMERGENCY_CONTACT_RELATION','EMERGENCY_CONTACT_NUMBER')),
        primary key (field)
    );

    create table candidate_profile_custom_values (
        profile_id uuid not null,
        field_code varchar(60) not null,
        field_value varchar(2000),
        primary key (profile_id, field_code)
    );

    create table candidate_profiles (
        date_of_birth date,
        revision integer not null,
        submitted_at timestamp(6) with time zone not null,
        updated_at timestamp(6) with time zone not null,
        pan_number varchar(10),
        candidate_id uuid not null unique,
        id uuid not null,
        aadhaar_number varchar(20),
        blood_group varchar(20) check (blood_group in ('A_POSITIVE','A_NEGATIVE','B_POSITIVE','B_NEGATIVE','AB_POSITIVE','AB_NEGATIVE','O_POSITIVE','O_NEGATIVE','UNKNOWN')),
        alternate_contact_number varchar(25),
        contact_number varchar(25),
        emergency_contact_number varchar(25),
        emergency_contact_relation varchar(30) check (emergency_contact_relation in ('FATHER','MOTHER','SPOUSE','SON','DAUGHTER','BROTHER','SISTER','GUARDIAN','RELATIVE','FRIEND','OTHER')),
        gender varchar(30) check (gender in ('MALE','FEMALE','OTHER','PREFER_NOT_TO_SAY')),
        submitted_from_ip varchar(60),
        emergency_contact_name varchar(160),
        fathers_name varchar(160),
        full_name_as_per_aadhaar varchar(160),
        personal_email varchar(180),
        permanent_address varchar(600),
        primary key (id)
    );

    create table candidate_required_documents (
        mandatory boolean not null,
        candidate_id uuid not null,
        custom_type_code varchar(60),
        document_type varchar(60) not null check (document_type in ('SSC_CERTIFICATE','SECONDARY_EDUCATION_CERTIFICATE','HIGHER_EDUCATION_PROVISIONAL','HIGHER_EDUCATION_ORIGINAL_DEGREE','HIGHER_EDUCATION_MARKSHEET','AADHAAR_ID','PAN_CARD','PASSPORT_PHOTO','ADDRESS_PROOF','EXPERIENCE_CERTIFICATE','RELIEVING_LETTER','PREVIOUS_OFFER_LETTER','PAYSLIP_MONTH_1','PAYSLIP_MONTH_2','PAYSLIP_MONTH_3','PAYSLIP_MONTH_4','PAYSLIP_MONTH_5','PAYSLIP_MONTH_6','PAYSLIPS','BANK_DETAILS','OTHER','EDUCATION_CERTIFICATE','INTERMEDIATE_CERTIFICATE','DIPLOMA_CERTIFICATE','UG_DEGREE_CERTIFICATE','PG_DEGREE_CERTIFICATE')),
        label varchar(160)
    );

    create table candidates (
        invitation_count integer not null,
        otp_attempts integer default 0 not null,
        completed_at timestamp(6) with time zone,
        created_at timestamp(6) with time zone not null,
        docs_approved_at timestamp(6) with time zone,
        invitation_sent_at timestamp(6) with time zone,
        last_portal_access_at timestamp(6) with time zone,
        last_verified_at timestamp(6) with time zone,
        otp_expires_at timestamp(6) with time zone,
        otp_sent_at timestamp(6) with time zone,
        submitted_for_review_at timestamp(6) with time zone,
        token_expires_at timestamp(6) with time zone,
        token_issued_at timestamp(6) with time zone,
        updated_at timestamp(6) with time zone not null,
        id uuid not null,
        stage varchar(40) not null check (stage in ('DOCS_PENDING','DOCS_APPROVED','OFFER_ACCEPTED')),
        department varchar(120) not null,
        job_role varchar(120) not null,
        invite_token_hash varchar(128),
        otp_hash varchar(128),
        name varchar(160) not null,
        created_by varchar(180) not null,
        email varchar(180) not null unique,
        invite_token_cipher varchar(400),
        primary key (id),
        constraint idx_candidates_token_hash unique (invite_token_hash)
    );

    create table custom_candidate_fields (
        archived boolean not null,
        enabled boolean not null,
        position integer not null,
        required boolean not null,
        created_at timestamp(6) with time zone not null,
        id uuid not null,
        field_group varchar(20) not null check (field_group in ('PERSONAL','IDENTITY','EMERGENCY','ADDITIONAL')),
        field_type varchar(20) not null check (field_type in ('TEXT','TEXTAREA','NUMBER','DATE','EMAIL','PHONE','SELECT')),
        code varchar(60) not null unique,
        label varchar(120) not null,
        created_by varchar(180),
        help_text varchar(300),
        options varchar(2000),
        primary key (id)
    );

    create table custom_document_types (
        archived boolean not null,
        enabled boolean not null,
        position integer not null,
        created_at timestamp(6) with time zone not null,
        id uuid not null,
        doc_group varchar(20) not null check (doc_group in ('EDUCATION','IDENTITY','EMPLOYMENT','PAYROLL','OTHER')),
        code varchar(60) not null unique,
        label varchar(160) not null,
        created_by varchar(180),
        description varchar(300),
        primary key (id)
    );

    create table hr_users (
        active boolean not null,
        created_at timestamp(6) with time zone not null,
        last_login_at timestamp(6) with time zone,
        id uuid not null,
        role varchar(20) not null check (role in ('HR','ADMIN')),
        job_title varchar(120),
        full_name varchar(160) not null,
        email varchar(180) not null unique,
        password_hash varchar(255) not null,
        primary key (id)
    );

    create table noc_fields (
        height_pct float(53) not null,
        page integer not null,
        position integer not null,
        width_pct float(53) not null,
        x_pct float(53) not null,
        y_pct float(53) not null,
        text_color varchar(7),
        noc_id uuid not null,
        text_font varchar(20) check (text_font in ('HELVETICA','TIMES','COURIER')),
        field_type varchar(30) not null check (field_type in ('SIGNATURE','NAME','TITLE','DATE','TEXT')),
        prefill varchar(2000),
        primary key (position, noc_id)
    );

    create table noc_packets (
        page_count integer,
        created_at timestamp(6) with time zone not null,
        sent_at timestamp(6) with time zone,
        signed_at timestamp(6) with time zone,
        size_bytes bigint,
        token_expires_at timestamp(6) with time zone,
        updated_at timestamp(6) with time zone not null,
        viewed_at timestamp(6) with time zone,
        id uuid not null,
        status varchar(20) not null check (status in ('DRAFT','SENT','VIEWED','SIGNED')),
        access_token_hash varchar(128),
        recipient_name varchar(160) not null,
        signed_by_name varchar(160),
        created_by varchar(180),
        recipient_email varchar(180) not null,
        title varchar(200),
        nda_filename varchar(260),
        noc_filename varchar(260),
        access_token_cipher varchar(400),
        signed_storage_key varchar(400),
        storage_key varchar(400) not null,
        primary key (id),
        constraint idx_noc_token unique (access_token_hash)
    );

    create table offer_fields (
        field_order integer not null,
        height_pct float(53) not null,
        page integer not null,
        width_pct float(53) not null,
        x_pct float(53) not null,
        y_pct float(53) not null,
        text_color varchar(7),
        offer_id uuid not null,
        text_font varchar(20) check (text_font in ('HELVETICA','TIMES','COURIER')),
        field_type varchar(30) not null check (field_type in ('SIGNATURE','NAME','TITLE','DATE','TEXT')),
        prefill varchar(2000),
        primary key (field_order, offer_id)
    );

    create table offers (
        accepted_at timestamp(6) with time zone,
        sent_at timestamp(6) with time zone not null,
        size_bytes bigint not null,
        viewed_at timestamp(6) with time zone,
        candidate_id uuid not null unique,
        id uuid not null,
        status varchar(30) not null check (status in ('DRAFT','SENT','VIEWED','ACCEPTED')),
        accepted_from_ip varchar(60),
        content_type varchar(120),
        accepted_by_name varchar(160),
        uploaded_by varchar(180) not null,
        original_filename varchar(260) not null,
        signed_storage_key varchar(400),
        storage_key varchar(400) not null,
        notes varchar(1000),
        primary key (id)
    );

    create index idx_audit_candidate 
       on audit_logs (candidate_id);

    create index idx_audit_event_type 
       on audit_logs (event_type);

    create index idx_audit_occurred_at 
       on audit_logs (occurred_at);

    create index idx_documents_candidate 
       on candidate_documents (candidate_id);

    create index idx_documents_status 
       on candidate_documents (status);

    create index idx_profile_custom_values 
       on candidate_profile_custom_values (profile_id);

    create index idx_required_docs_candidate 
       on candidate_required_documents (candidate_id);

    create index idx_candidates_stage 
       on candidates (stage);

    create index idx_candidates_token_expiry 
       on candidates (token_expires_at);

    create index idx_candidates_created_at 
       on candidates (created_at);

    create index idx_noc_fields_packet 
       on noc_fields (noc_id);

    create index idx_noc_status 
       on noc_packets (status);

    create index idx_noc_recipient 
       on noc_packets (recipient_email);

    create index idx_offer_fields_offer 
       on offer_fields (offer_id);

    create index idx_offers_status 
       on offers (status);

    alter table if exists candidate_documents 
       add constraint fk_documents_candidate 
       foreign key (candidate_id) 
       references candidates;

    alter table if exists candidate_profile_custom_values 
       add constraint FKkpwtymqf095omf037bn1wsbg8 
       foreign key (profile_id) 
       references candidate_profiles;

    alter table if exists candidate_profiles 
       add constraint fk_profiles_candidate 
       foreign key (candidate_id) 
       references candidates;

    alter table if exists candidate_required_documents 
       add constraint fk_required_docs_candidate 
       foreign key (candidate_id) 
       references candidates;

    alter table if exists noc_fields 
       add constraint fk_noc_fields_packet 
       foreign key (noc_id) 
       references noc_packets;

    alter table if exists offer_fields 
       add constraint fk_offer_fields_offer 
       foreign key (offer_id) 
       references offers;

    alter table if exists offers 
       add constraint fk_offers_candidate 
       foreign key (candidate_id) 
       references candidates;
