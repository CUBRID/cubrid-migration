-- Executed as informix against sysmaster to create the e2e source database.
-- WITH LOG makes it a logged (transactional) database, which CMT requires.
CREATE DATABASE e2e_db WITH LOG;
