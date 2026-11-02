-- MediCircle : Unused-Medicine Redistribution Network
-- MySQL 8+
DROP DATABASE IF EXISTS medicircle;
CREATE DATABASE medicircle;
USE medicircle;

CREATE TABLE users (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(80)  NOT NULL,
  email      VARCHAR(100) NOT NULL UNIQUE,
  phone      VARCHAR(15),
  city       VARCHAR(50)  NOT NULL,
  role       ENUM('DONOR','NGO') NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE medicines (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  donor_id    INT NOT NULL,
  name        VARCHAR(100) NOT NULL,
  quantity    INT NOT NULL CHECK (quantity > 0),
  expiry_date DATE NOT NULL,
  status      ENUM('AVAILABLE','RESERVED','DELIVERED','EXPIRED') DEFAULT 'AVAILABLE',
  added_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (donor_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_med_name (name),
  INDEX idx_med_status_exp (status, expiry_date)
);

CREATE TABLE requests (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  ngo_id       INT NOT NULL,
  medicine_id  INT NOT NULL,
  status       ENUM('PENDING','DELIVERED','CANCELLED') DEFAULT 'PENDING',
  requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (ngo_id) REFERENCES users(id),
  FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE
);

CREATE TABLE audit_log (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  medicine_id INT,
  action      VARCHAR(50),
  at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DELIMITER $$
-- Reject medicines that expire within 30 days (unsafe to donate)
CREATE TRIGGER trg_med_check BEFORE INSERT ON medicines FOR EACH ROW
BEGIN
  IF NEW.expiry_date < DATE_ADD(CURDATE(), INTERVAL 30 DAY) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Medicine must have at least 30 days before expiry';
  END IF;
END$$

-- Reserving a medicine automatically locks it
CREATE TRIGGER trg_prevent_self_reserve BEFORE INSERT ON requests FOR EACH ROW
BEGIN
  DECLARE med_donor_id INT;
  SELECT donor_id INTO med_donor_id FROM medicines WHERE id = NEW.medicine_id;
  IF NEW.ngo_id = med_donor_id THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflict of Interest: A donor cannot reserve their own medicine';
  END IF;
END$$

CREATE TRIGGER trg_request_reserve AFTER INSERT ON requests FOR EACH ROW
BEGIN
  UPDATE medicines SET status='RESERVED' WHERE id = NEW.medicine_id;
  INSERT INTO audit_log(medicine_id, action) VALUES (NEW.medicine_id, 'RESERVED');
END$$

CREATE TRIGGER trg_request_update AFTER UPDATE ON requests FOR EACH ROW
BEGIN
  IF NEW.status='DELIVERED' THEN
    UPDATE medicines SET status='DELIVERED' WHERE id = NEW.medicine_id;
  ELSEIF NEW.status='CANCELLED' THEN
    UPDATE medicines SET status='AVAILABLE' WHERE id = NEW.medicine_id;
  END IF;
  INSERT INTO audit_log(medicine_id, action) VALUES (NEW.medicine_id, NEW.status);
END$$

-- Housekeeping procedure: mark expired stock
CREATE PROCEDURE mark_expired()
BEGIN
  UPDATE medicines SET status='EXPIRED'
  WHERE expiry_date < CURDATE() AND status='AVAILABLE';
END$$
DELIMITER ;

-- Available stock with donor info and days left
CREATE VIEW v_available AS
SELECT m.id, m.donor_id, m.name, m.quantity, m.expiry_date,
       DATEDIFF(m.expiry_date, CURDATE()) AS days_left,
       u.name AS donor, u.city, u.phone
FROM medicines m JOIN users u ON u.id = m.donor_id
WHERE m.status='AVAILABLE' AND m.expiry_date >= CURDATE();

-- Impact report: per-city lives touched
CREATE VIEW v_impact AS
SELECT u.city, COUNT(*) AS delivered_items, SUM(m.quantity) AS units_saved
FROM medicines m JOIN users u ON u.id = m.donor_id
WHERE m.status='DELIVERED' GROUP BY u.city;

-- Sample data
INSERT INTO users(name,email,phone,city,role) VALUES
('Ravi Kumar','ravi@mail.com','9000000001','Chennai','DONOR'),
('Priya S','priya@mail.com','9000000002','Bengaluru','DONOR'),
('Helping Hands NGO','hh@ngo.org','9000000003','Chennai','NGO'),
('CareFirst Trust','cf@ngo.org','9000000004','Bengaluru','NGO');

INSERT INTO medicines(donor_id,name,quantity,expiry_date) VALUES
(1,'Paracetamol 500mg',20,DATE_ADD(CURDATE(),INTERVAL 120 DAY)),
(1,'Metformin 500mg',30,DATE_ADD(CURDATE(),INTERVAL 45 DAY)),
(2,'Paracetamol 500mg',10,DATE_ADD(CURDATE(),INTERVAL 60 DAY)),
(2,'Amoxicillin 250mg',14,DATE_ADD(CURDATE(),INTERVAL 200 DAY));
