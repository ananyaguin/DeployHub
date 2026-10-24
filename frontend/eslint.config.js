import js from '@eslint/js'
import globals from 'globals'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'

export default [
  { ignores: ['dist'] },
  js.configs.recommended,
  {
    files: ['src/**/*.{js,jsx}'],
    ...react.configs.flat.recommended,
    languageOptions: {
      ...react.configs.flat.recommended.languageOptions,
      ecmaVersion: 2024,
      globals: globals.browser,
    },
    settings: { react: { version: '18.3' } },
    rules: {
      ...react.configs.flat.recommended.rules,
      'react/prop-types': 'off',
    },
  },
  react.configs.flat['jsx-runtime'],
  reactHooks.configs.flat.recommended,
  {
    files: ['src/**/*.{js,jsx}'],
    plugins: { 'react-refresh': reactRefresh },
    rules: {
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
  },
]
