import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { GraduationCap, Loader2, AlertCircle } from 'lucide-react';

export function LoginPage() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Redirect if already logged in
  if (isAuthenticated) {
    navigate('/dashboard', { replace: true });
    return null;
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await login({ username, password });
      navigate('/dashboard');
    } catch (err: any) {
      const message = err.response?.data?.error || 'Innlogging feilet. Prøv igjen.';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg-primary p-4">
      {/* Background gradient */}
      <div className="absolute inset-0 overflow-hidden">
        <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-accent/5 rounded-full blur-3xl" />
        <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-purple-500/5 rounded-full blur-3xl" />
      </div>

      <div className="relative w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-accent/10 border border-accent/20 rounded-2xl mb-4">
            <GraduationCap size={32} className="text-accent" />
          </div>
          <h1 className="text-2xl font-bold text-text-primary">Verdan Skoleadministrasjon</h1>
          <p className="text-text-secondary text-sm mt-1">Logg inn på din konto</p>
        </div>

        {/* Form card */}
        <div className="bg-bg-secondary border border-border rounded-2xl p-8 shadow-xl shadow-black/20">
          <form onSubmit={handleSubmit} className="space-y-5">
            {error && (
              <div className="flex items-center gap-2 p-3 bg-danger/10 border border-danger/20 rounded-lg text-danger text-sm">
                <AlertCircle size={16} />
                <span>{error}</span>
              </div>
            )}

            <div>
              <label
                htmlFor="username"
                className="block text-sm font-medium text-text-secondary mb-1.5"
              >
                Brukernavn eller e-post
              </label>
              <input
                id="username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Skriv inn brukernavn eller e-post"
                required
                autoFocus
                className="w-full px-4 py-2.5 bg-bg-input border border-border rounded-lg text-text-primary placeholder:text-text-muted text-sm
                  focus:outline-none focus:border-border-focus focus:ring-1 focus:ring-border-focus transition-colors"
              />
            </div>

            <div>
              <label
                htmlFor="password"
                className="block text-sm font-medium text-text-secondary mb-1.5"
              >
                Passord
              </label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Skriv inn passordet ditt"
                required
                className="w-full px-4 py-2.5 bg-bg-input border border-border rounded-lg text-text-primary placeholder:text-text-muted text-sm
                  focus:outline-none focus:border-border-focus focus:ring-1 focus:ring-border-focus transition-colors"
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 bg-accent hover:bg-accent-hover text-white font-medium rounded-lg text-sm
                transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              {loading ? (
                <>
                  <Loader2 size={16} className="animate-spin" />
                  Logger inn...
                </>
              ) : (
                'Logg inn'
              )}
            </button>
          </form>

          <div className="mt-6 pt-5 border-t border-border">
            <p className="text-xs text-text-muted text-center">Demokontoer</p>
            <div className="grid grid-cols-4 gap-2 mt-3">
              {[
                { user: 'admin', pass: 'admin123', role: 'Superadmin' },
                { user: 'inst-admin', pass: '123456', role: 'Inst.admin' },
                { user: 'teacher', pass: 'teacher123', role: 'Lærer' },
                { user: 'student', pass: 'student123', role: 'Elev' },
              ].map((demo) => (
                <button
                  key={demo.user}
                  type="button"
                  onClick={() => {
                    setUsername(demo.user);
                    setPassword(demo.pass);
                  }}
                  className="text-xs py-1.5 px-2 rounded-md bg-bg-hover hover:bg-border text-text-secondary hover:text-text-primary transition-colors"
                >
                  {demo.role}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
