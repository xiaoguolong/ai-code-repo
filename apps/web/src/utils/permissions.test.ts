import { describe, expect, it } from 'vitest';
import { canRunAgents, isAdmin, menuForRole, runDeniedReason } from './permissions';

describe('permissions', () => {
  it('admin sees user management', () => {
    const menu = menuForRole('admin');
    expect(menu.map((item) => item.key)).toContain('/users');
    expect(menu.map((item) => item.key)).toContain('/agents/patient-analysis');
  });

  it('operator does not see user management but can run agents', () => {
    const menu = menuForRole('operator');
    expect(menu.map((item) => item.key)).not.toContain('/users');
    expect(canRunAgents('operator')).toBe(true);
    expect(runDeniedReason('operator')).toBeNull();
  });

  it('viewer is read only', () => {
    expect(canRunAgents('viewer')).toBe(false);
    expect(runDeniedReason('viewer')).toContain('只读');
    expect(isAdmin('viewer')).toBe(false);
  });

  it('unknown role gets base menu only', () => {
    expect(menuForRole('unknown').map((item) => item.key)).not.toContain('/users');
  });
});
