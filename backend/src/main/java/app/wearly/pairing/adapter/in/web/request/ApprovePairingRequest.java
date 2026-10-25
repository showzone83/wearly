package app.wearly.pairing.adapter.in.web.request;

/**
 * POST /pairings/{id}/approve 본문.
 *
 * @param permission 줄 권한 (VIEW · EDIT)
 */
public record ApprovePairingRequest(String permission) {}
