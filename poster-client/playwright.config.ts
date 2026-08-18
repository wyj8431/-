import { existsSync } from 'node:fs'
import { defineConfig } from '@playwright/test'

const localChrome = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const launchOptions = existsSync(localChrome) ? { executablePath: localChrome } : undefined

export default defineConfig({
  testDir: './e2e',
  use: {
    baseURL: 'http://127.0.0.1:5173',
    launchOptions,
  },
  projects: [
    { name: 'desktop', use: { browserName: 'chromium', viewport: { width: 1440, height: 960 } } },
    {
      name: 'mobile',
      use: {
        browserName: 'chromium',
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,
        deviceScaleFactor: 3,
      },
    },
  ],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: !process.env.CI,
  },
})
