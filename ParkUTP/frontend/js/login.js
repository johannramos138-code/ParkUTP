document.getElementById('loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    try {
        const data = await API.login(username, password);

        if (data.status === 'OK') {
            localStorage.setItem('username', data.username);
            localStorage.setItem('role', data.role);
            if (data.role === 'ADMIN') {
                window.location.href = '/admin.html';
            } else {
                window.location.href = '/usuario.html';
            }
        } else {
            document.getElementById('errorMsg').textContent = data.message || 'Error';
        }
    } catch (err) {
        console.error('Error:', err);
        document.getElementById('errorMsg').textContent = 'Error de conexion';
    }
});