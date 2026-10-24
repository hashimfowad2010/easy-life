import { defineConfig } from "vitest/config";

// Firestore security rules tests. They need the Firestore emulator:
//   cd firebase && npx firebase-tools emulators:exec --only firestore --project demo-easylife "npm --prefix functions run test:rules"
export default defineConfig({
  test: {
    include: ["test-rules/**/*.test.ts"],
    testTimeout: 20_000,
    fileParallelism: false,
  },
});
