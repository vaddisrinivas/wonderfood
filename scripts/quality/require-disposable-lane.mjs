#!/usr/bin/env node

const PROVIDER_ACK = 'DISPOSABLE_PROVIDER_ONLY';
const DEVICE_ACK = 'DISPOSABLE_EMULATOR_ONLY';
const unsafeLabel = /(^|[-_:])(prod|production|personal|primary|default|real)([-_:]|$)/i;

export function validateDisposableLane(lane, env = process.env) {
  if (lane === 'provider') {
    if (env.WONDERFOOD_LIVE_PROVIDER_ACK !== PROVIDER_ACK) {
      throw new Error(`set WONDERFOOD_LIVE_PROVIDER_ACK=${PROVIDER_ACK}`);
    }
    const target = env.WONDERFOOD_DISPOSABLE_PROVIDER_TARGET?.trim();
    if (!target) throw new Error('set WONDERFOOD_DISPOSABLE_PROVIDER_TARGET to the isolated fixture name');
    if (unsafeLabel.test(target)) throw new Error('provider target label is not disposable');
    return;
  }

  if (lane === 'device') {
    if (env.WONDERFOOD_DEVICE_MUTATION_ACK !== DEVICE_ACK) {
      throw new Error(`set WONDERFOOD_DEVICE_MUTATION_ACK=${DEVICE_ACK}`);
    }
    const serial = (env.LIFEOS_ANDROID_SERIAL || env.ANDROID_SERIAL || '').trim();
    const avd = env.LIFEOS_EMULATOR_AVD?.trim();
    if (!serial && !avd) throw new Error('set an explicit emulator serial or LIFEOS_EMULATOR_AVD');
    if (serial && !serial.startsWith('emulator-')) {
      throw new Error('physical devices are forbidden in the disposable device lane');
    }
    if (avd && unsafeLabel.test(avd)) throw new Error('emulator target label is not disposable');
    return;
  }

  throw new Error('lane must be provider or device');
}

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  try {
    validateDisposableLane(process.argv[2]);
    console.log(`Disposable ${process.argv[2]} lane: authorized`);
  } catch (error) {
    console.error(`Disposable lane guard: BLOCKED (${error instanceof Error ? error.message : 'invalid configuration'}). No mutation attempted.`);
    process.exit(2);
  }
}
