import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import eslintConfigPrettier from 'eslint-config-prettier';

export default tseslint.config(
  // Ignorer build-output og dependencies
  { ignores: ['dist', 'node_modules'] },

  // Basis JavaScript-regler
  js.configs.recommended,

  // TypeScript-regler
  ...tseslint.configs.recommended,

  // React-spesifikke regler
  {
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      // React Hooks — sørg for korrekt bruk av useEffect, useMemo, etc.
      ...reactHooks.configs.recommended.rules,
      'react-hooks/purity': 'warn',
      'react-hooks/immutability': 'warn',
      'react-hooks/set-state-in-effect': 'warn',

      // React Refresh — advarer om komponenter som ikke kan hot-reloades
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],

      // TypeScript — tillat noen vanlige mønstre
      '@typescript-eslint/no-unused-vars': ['warn', {
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
      }],
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-expressions': 'warn',

      // Generelt
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      'no-empty': ['warn', { allowEmptyCatch: true }],
    },
  },

  // Prettier — slår av formateringsregler som kolliderer
  eslintConfigPrettier,
);
