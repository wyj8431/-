# Login Dialog Visual Restoration

**Date:** 2026-08-18
**Status:** Complete

## Delivered Scope

- Rebuilt the homepage login dialog as a responsive four-mode state machine: phone verification, WeChat QR, account password, and registration.
- Kept the existing `login({ phone, verificationCode })` event and the homepage pending-template session flow unchanged. Password, QR help, provider, and registration-only actions show explicit later-phase notices instead of calling nonexistent APIs.
- Matched the reference structure with a blue promotional panel, six benefits, phone/code form, password and registration controls, QR placeholder, dashed provider separator, agreement strip, and mobile layout.
- Added six local provider icon assets in the required order: phone, QQ, Weibo, WeChat, DingTalk, and Baidu.
- Tuned the desktop dialog to the reference proportion (`900 x 590px`) and replaced provider artwork plus the QR image with local crops from the supplied reference screenshot.
- Added focused unit coverage for state switching, provider ordering, and the existing login payload; extended Playwright coverage for desktop and 390px mobile dialog behavior and screenshots.

## Verification

| Area | Command | Result |
| --- | --- | --- |
| Focused dialog unit | `poster-client\npm run test:unit -- src/features/auth/__tests__/LoginDialog.spec.ts` | 4 passed |
| Frontend unit | `poster-client\npm run test:unit` | 22 passed |
| Frontend production build | `poster-client\npm run build` | passed |
| Browser regression | `poster-client\npm run test:e2e` | 7 passed, 1 desktop-only case skipped |
| Visual check | Playwright desktop + mobile dialog screenshots | passed; no mobile horizontal overflow observed |
| Diff hygiene | `git diff --check` | passed |

## API Boundary

No auth API, session store, or homepage intent-recovery contract was changed for this visual stage. Real password login, WeChat OAuth, standalone registration, and SMS dispatch remain later-phase work.
