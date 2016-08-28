AFRAME.registerComponent('fireballDisappear', {
  init: function () {
    var el = this.el;
    var fb = document.getElementById('fireball');

    el.addEventListener('click', function () {
      fb.setAttribute('visible', false);
    })
  }
});
