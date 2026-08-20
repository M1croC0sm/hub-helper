# Supplemental Policy Rules

This file records rules reported by the user that are not printed in the
currently available attendance-policy document. They must remain distinguishable
from transcribed source text in the app.

## Reaching Seven Points Twice

**Reported rule:** If an employee reaches seven attendance points twice within
one year, the employee is dismissed.

**Source status:** User-reported supplemental rule; not present in the supplied
printed attendance-policy transcription. The user believes “one year” means a
rolling 12-month period but will confirm this later.

**Provisional interpretation:** Count two distinct occasions on which the
employee reaches seven points within a rolling 12-month window. This is a
working assumption for clearly labeled warnings only, not a confirmed rule for
automatic dismissal predictions.

**Automation status:** Do not automatically predict dismissal until the exact
written rule or an authoritative clarification establishes:

- confirmation that “one year” means a rolling 12-month period;
- what counts as “reaching seven points” a second time (for example, a second
  seven-point disciplinary event after the balance first falls below seven);
- how corrections, rescissions, disputes, attendance credits, and annual point
  expirations affect the count;
- whether the outcome is automatic termination or discipline subject to review;
  and
- the rule's effective date and which employees it covers.

Until those details are confirmed, Hub Helper may warn that the reported rule
could apply and show the relevant event history, but it must label the warning
as unverified and must not state that dismissal is certain.

## Paid Sick Day Duration

**Confirmed operational rule:** The annual paid sick day is eight (8) hours for
both first-shift and second-shift employees.

**Source status:** Confirmed by the user on August 16, 2026. Article 36 of the
supplied CBA establishes one paid sick-leave day per year in a full-day
increment with no rollover, but does not specify the number of hours for each
shift. The eight-hour value must therefore remain labeled as a user-confirmed
operational rule rather than quoted contract language.

**App behavior:** The January 1 sick-time reset and the quick “Use sick day”
action use eight hours regardless of the selected shift. A balance correction
in Settings may override the result when a pay stub differs.
